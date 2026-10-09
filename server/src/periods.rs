use crate::{
    App,
    error::{Error, Result},
};
use axum::{Json, extract::State, http::StatusCode};
use chrono::{Days, NaiveDate};
use serde::Deserialize;
use serde_json::Value;

// 单条语句读取整份文档，保证 revision 与区间、备注来自同一快照。
const DOCUMENT_SQL: &str = r#"SELECT jsonb_build_object(
    'revision',s.revision,
    'settings',jsonb_build_object('cycle_length',s.cycle_length,'period_length',s.period_length,'luteal_length',s.luteal_length),
    'ranges',COALESCE((SELECT jsonb_agg(jsonb_build_object('start',r.start_date,'end',r.end_date) ORDER BY r.start_date)
        FROM period_ranges r),'[]'::jsonb),
    'notes',COALESCE((SELECT jsonb_agg(jsonb_build_object('date',n.day,'mood',n.mood,'text',n.text) ORDER BY n.day)
        FROM period_notes n),'[]'::jsonb)
    ) FROM period_state s"#;

const MOODS: [&str; 5] = ["happy", "calm", "tired", "low", "irritable"];
const MAX_NOTE_CHARS: usize = 500;
const MAX_CLOSED_DAYS: u64 = 31;

#[derive(Clone, Copy, Debug, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Settings {
    pub cycle_length: i16,
    pub period_length: i16,
    pub luteal_length: i16,
}

/// 一次经期；`end` 为空表示进行中。
#[derive(Clone, Debug, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Range {
    pub start: NaiveDate,
    pub end: Option<NaiveDate>,
}

#[derive(Clone, Debug, Deserialize, PartialEq)]
#[serde(deny_unknown_fields)]
pub struct Note {
    pub date: NaiveDate,
    pub mood: Option<String>,
    #[serde(default)]
    pub text: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Input {
    pub base_revision: i64,
    pub settings: Settings,
    pub ranges: Vec<Range>,
    pub notes: Vec<Note>,
}

fn conflict() -> Error {
    Error(StatusCode::CONFLICT, "经期记录已在其他设备更新")
}

fn in_bounds(date: NaiveDate) -> Result<()> {
    let earliest = NaiveDate::from_ymd_opt(2000, 1, 1).expect("valid date");
    let latest = NaiveDate::from_ymd_opt(2100, 12, 31).expect("valid date");
    if date < earliest || date > latest {
        return Err(Error::bad("日期需在 2000 年至 2100 年之间"));
    }
    Ok(())
}

/// 校验整份文档并排序、去除备注首尾空白；违反任一规则即拒绝，不替客户端合并区间。
pub fn validate(mut input: Input) -> Result<Input> {
    let settings = input.settings;
    if input.base_revision < 0
        || !(15..=60).contains(&settings.cycle_length)
        || !(2..=10).contains(&settings.period_length)
        || !(10..=16).contains(&settings.luteal_length)
    {
        return Err(Error::bad(
            "周期需为 15–60 天、经期 2–10 天、黄体期 10–16 天",
        ));
    }

    input.ranges.sort_by_key(|range| range.start);
    let last = input.ranges.len().saturating_sub(1);
    for (index, range) in input.ranges.iter().enumerate() {
        in_bounds(range.start)?;
        let Some(end) = range.end else {
            if index != last {
                return Err(Error::bad("只能有一段进行中的经期，且必须是最近一段"));
            }
            continue;
        };
        in_bounds(end)?;
        if end < range.start {
            return Err(Error::bad("经期结束日期不能早于开始日期"));
        }
        if (end - range.start).num_days() as u64 >= MAX_CLOSED_DAYS {
            return Err(Error::bad("单次经期不能超过 31 天"));
        }
        let next_free = end.checked_add_days(Days::new(1)).unwrap_or(end);
        if input
            .ranges
            .get(index + 1)
            .is_some_and(|next| next.start <= next_free)
        {
            return Err(Error::bad("经期区间不能重叠或相邻"));
        }
    }

    for note in &mut input.notes {
        note.text = note.text.trim().to_owned();
    }
    input.notes.sort_by_key(|note| note.date);
    for (index, note) in input.notes.iter().enumerate() {
        in_bounds(note.date)?;
        if index > 0 && input.notes[index - 1].date == note.date {
            return Err(Error::bad("同一天只能有一条心情与备注"));
        }
        if note
            .mood
            .as_deref()
            .is_some_and(|mood| !MOODS.contains(&mood))
        {
            return Err(Error::bad("心情无效"));
        }
        if note.text.chars().count() > MAX_NOTE_CHARS {
            return Err(Error::bad("备注最多 500 字"));
        }
        if note.mood.is_none() && note.text.is_empty() {
            return Err(Error::bad("心情和备注不能都为空"));
        }
    }
    Ok(input)
}

async fn document<'e>(db: impl sqlx::PgExecutor<'e>) -> Result<Value> {
    Ok(sqlx::query_scalar(DOCUMENT_SQL).fetch_one(db).await?)
}

/// 读取整份经期文档；数据不分账号，因此不要求 `X-Account-ID`。
pub async fn get(State(app): State<App>) -> Result<Json<Value>> {
    Ok(Json(document(&app.db).await?))
}

/// 以 `base_revision` 做乐观并发，整份替换区间、备注与设置，成功后 revision 加 1。
pub async fn replace(State(app): State<App>, Json(input): Json<Input>) -> Result<Json<Value>> {
    let input = validate(input)?;
    let mut tx = app.db.begin().await?;
    let revision: i64 = sqlx::query_scalar("SELECT revision FROM period_state FOR UPDATE")
        .fetch_one(&mut *tx)
        .await?;
    if revision != input.base_revision {
        return Err(conflict());
    }
    sqlx::query("DELETE FROM period_ranges")
        .execute(&mut *tx)
        .await?;
    sqlx::query("DELETE FROM period_notes")
        .execute(&mut *tx)
        .await?;
    let (starts, ends): (Vec<NaiveDate>, Vec<Option<NaiveDate>>) = input
        .ranges
        .iter()
        .map(|range| (range.start, range.end))
        .unzip();
    sqlx::query(
        "INSERT INTO period_ranges (start_date,end_date) SELECT * FROM UNNEST($1::date[],$2::date[])",
    )
    .bind(starts)
    .bind(ends)
    .execute(&mut *tx)
    .await?;
    let days: Vec<NaiveDate> = input.notes.iter().map(|note| note.date).collect();
    let moods: Vec<Option<String>> = input.notes.iter().map(|note| note.mood.clone()).collect();
    let texts: Vec<String> = input.notes.iter().map(|note| note.text.clone()).collect();
    sqlx::query(
        "INSERT INTO period_notes (day,mood,text) SELECT * FROM UNNEST($1::date[],$2::text[],$3::text[])",
    )
    .bind(days)
    .bind(moods)
    .bind(texts)
    .execute(&mut *tx)
    .await?;
    sqlx::query("UPDATE period_state SET revision=revision+1,cycle_length=$1,period_length=$2,luteal_length=$3")
        .bind(input.settings.cycle_length)
        .bind(input.settings.period_length)
        .bind(input.settings.luteal_length)
        .execute(&mut *tx)
        .await?;
    let saved = document(&mut *tx).await?;
    tx.commit().await?;
    Ok(Json(saved))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn date(text: &str) -> NaiveDate {
        text.parse().unwrap()
    }

    fn range(start: &str, end: Option<&str>) -> Range {
        Range {
            start: date(start),
            end: end.map(date),
        }
    }

    fn note(day: &str, mood: Option<&str>, text: &str) -> Note {
        Note {
            date: date(day),
            mood: mood.map(str::to_owned),
            text: text.to_owned(),
        }
    }

    fn input(ranges: Vec<Range>, notes: Vec<Note>) -> Input {
        Input {
            base_revision: 0,
            settings: Settings {
                cycle_length: 28,
                period_length: 5,
                luteal_length: 14,
            },
            ranges,
            notes,
        }
    }

    fn rejection(input: Input) -> &'static str {
        match validate(input) {
            Ok(_) => panic!("expected rejection"),
            Err(error) => {
                assert_eq!(error.0, StatusCode::BAD_REQUEST);
                error.1
            }
        }
    }

    #[test]
    fn validate_sorts_ranges_and_trims_notes() {
        let valid = validate(input(
            vec![
                range("2026-10-08", None),
                range("2026-09-12", Some("2026-09-16")),
            ],
            vec![
                note("2026-10-09", None, "  腰酸  "),
                note("2026-10-08", Some("tired"), ""),
            ],
        ))
        .unwrap_or_else(|error| panic!("{}", error.1));
        assert_eq!(valid.ranges[0].start, date("2026-09-12"));
        assert_eq!(valid.ranges[1].end, None);
        assert_eq!(valid.notes[0].date, date("2026-10-08"));
        assert_eq!(valid.notes[1].text, "腰酸");
    }

    #[test]
    fn validate_rejects_overlapping_or_adjacent_ranges() {
        let overlap = input(
            vec![
                range("2026-09-12", Some("2026-09-16")),
                range("2026-09-16", Some("2026-09-18")),
            ],
            vec![],
        );
        assert_eq!(rejection(overlap), "经期区间不能重叠或相邻");
        let adjacent = input(
            vec![
                range("2026-09-12", Some("2026-09-16")),
                range("2026-09-17", None),
            ],
            vec![],
        );
        assert_eq!(rejection(adjacent), "经期区间不能重叠或相邻");
        let gap = input(
            vec![
                range("2026-09-12", Some("2026-09-16")),
                range("2026-09-18", None),
            ],
            vec![],
        );
        assert!(validate(gap).is_ok());
    }

    #[test]
    fn validate_allows_only_latest_range_ongoing() {
        let two = input(
            vec![range("2026-09-12", None), range("2026-10-08", None)],
            vec![],
        );
        assert_eq!(rejection(two), "只能有一段进行中的经期，且必须是最近一段");
    }

    #[test]
    fn validate_rejects_bad_range_dates() {
        let reversed = input(vec![range("2026-09-12", Some("2026-09-11"))], vec![]);
        assert_eq!(rejection(reversed), "经期结束日期不能早于开始日期");
        let long = input(vec![range("2026-09-01", Some("2026-10-02"))], vec![]);
        assert_eq!(rejection(long), "单次经期不能超过 31 天");
        let longest = input(vec![range("2026-09-01", Some("2026-10-01"))], vec![]);
        assert!(validate(longest).is_ok());
        let ancient = input(vec![range("1999-12-31", None)], vec![]);
        assert_eq!(rejection(ancient), "日期需在 2000 年至 2100 年之间");
    }

    #[test]
    fn validate_rejects_invalid_notes() {
        let duplicate = input(
            vec![],
            vec![
                note("2026-10-08", Some("calm"), ""),
                note("2026-10-08", None, "备注"),
            ],
        );
        assert_eq!(rejection(duplicate), "同一天只能有一条心情与备注");
        let mood = input(vec![], vec![note("2026-10-08", Some("angry"), "")]);
        assert_eq!(rejection(mood), "心情无效");
        let long = input(vec![], vec![note("2026-10-08", None, &"字".repeat(501))]);
        assert_eq!(rejection(long), "备注最多 500 字");
        let longest = input(vec![], vec![note("2026-10-08", None, &"字".repeat(500))]);
        assert!(validate(longest).is_ok());
        let blank = input(vec![], vec![note("2026-10-08", None, "   ")]);
        assert_eq!(rejection(blank), "心情和备注不能都为空");
    }

    #[test]
    fn validate_rejects_settings_out_of_range() {
        let mut short = input(vec![], vec![]);
        short.settings.cycle_length = 14;
        assert!(validate(short).is_err());
        let mut luteal = input(vec![], vec![]);
        luteal.settings.luteal_length = 17;
        assert!(validate(luteal).is_err());
        let mut revision = input(vec![], vec![]);
        revision.base_revision = -1;
        assert!(validate(revision).is_err());
    }
}
