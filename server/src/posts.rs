use crate::{
    App,
    accounts::Actor,
    error::{Error, Result},
    media,
};
use axum::{
    Json,
    extract::{Path, Query, State},
    http::StatusCode,
};
use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use uuid::Uuid;

#[derive(Default, Deserialize)]
pub struct PageQuery {
    pub cursor: Option<String>,
    pub limit: Option<i64>,
    pub author_id: Option<String>,
    pub q: Option<String>,
}
impl PageQuery {
    pub fn limit(&self) -> i64 {
        self.limit.unwrap_or(20).clamp(1, 50)
    }
    pub fn cursor(&self) -> Result<(Option<DateTime<Utc>>, Option<Uuid>)> {
        let Some(cursor) = &self.cursor else {
            return Ok((None, None));
        };
        let (time, id) = cursor
            .split_once(':')
            .ok_or_else(|| Error::bad("分页游标无效"))?;
        let date = time
            .parse::<i64>()
            .ok()
            .and_then(DateTime::from_timestamp_micros)
            .ok_or_else(|| Error::bad("分页游标无效"))?;
        let id = id.parse().map_err(|_| Error::bad("分页游标无效"))?;
        Ok((Some(date), Some(id)))
    }
}
pub fn cursor(time: DateTime<Utc>, id: Uuid) -> String {
    format!("{}:{id}", time.timestamp_micros())
}

pub const COMMENT_JSON: &str = r#"jsonb_build_object(
    'id',c.id,'author_id',c.author_id,'author_name',a.name,'avatar_id',a.avatar_id,
    'text',c.text,'media_id',c.media_id,'reply_to_id',c.reply_to_id,
    'media_info',(SELECT jsonb_build_object('bytes',m.bytes,'thumbnail_bytes',m.thumbnail_bytes,'preview_bytes',m.preview_bytes)
        FROM media m WHERE m.id=c.media_id AND NOT c.deleted),
    'reply_to_name',ra.name,'deleted',c.deleted,'timestamp',(extract(epoch from c.created_at)*1000)::bigint)"#;

pub async fn value(db: &sqlx::PgPool, actor: &str, id: Uuid) -> Result<Value> {
    // SQL 仅拼接本模块的常量片段，所有请求数据使用 bind。
    let sql = format!(
        r#"SELECT jsonb_build_object(
        'id',p.id,'author_id',p.author_id,'author_name',a.name,'avatar_id',a.avatar_id,
        'text',p.text,'location',p.location,'location_address',p.location_address,'visibility',p.visibility,
        'timestamp',(extract(epoch from p.created_at)*1000)::bigint,
        'photos',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',m.id,'width',m.width,'height',m.height,
            'bytes',m.bytes,'thumbnail_bytes',m.thumbnail_bytes,'preview_bytes',m.preview_bytes) ORDER BY pm.position)
            FROM post_media pm JOIN media m ON m.id=pm.media_id WHERE pm.post_id=p.id),'[]'::jsonb),
        'likes',COALESCE((SELECT jsonb_agg(jsonb_build_object('id',a.id,'name',a.name,'avatar_id',a.avatar_id) ORDER BY l.created_at)
            FROM likes l JOIN accounts a ON a.id=l.account_id WHERE l.post_id=p.id),'[]'::jsonb),
        'comment_count',(SELECT count(*) FROM comments c WHERE c.post_id=p.id),
        'comments',COALESCE((SELECT jsonb_agg(v ORDER BY t,id) FROM (
            SELECT {COMMENT_JSON} v,c.created_at t,c.id FROM comments c JOIN accounts a ON a.id=c.author_id
            LEFT JOIN comments r ON r.id=c.reply_to_id LEFT JOIN accounts ra ON ra.id=r.author_id
            WHERE c.post_id=p.id ORDER BY c.created_at DESC,c.id DESC LIMIT 3) preview),'[]'::jsonb)
        ) FROM posts p JOIN accounts a ON a.id=p.author_id
        WHERE p.id=$1 AND NOT p.deleted AND (p.visibility='public' OR p.author_id=$2)"#
    );
    sqlx::query_scalar(sqlx::AssertSqlSafe(sql))
        .bind(id)
        .bind(actor)
        .fetch_optional(db)
        .await?
        .ok_or_else(Error::missing)
}

pub async fn lock_visible(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    actor: &str,
    id: Uuid,
    own: bool,
) -> Result<String> {
    let author: Option<String> = sqlx::query_scalar("SELECT author_id FROM posts WHERE id=$1 AND NOT deleted AND (author_id=$2 OR (visibility='public' AND NOT $3)) FOR UPDATE")
        .bind(id).bind(actor).bind(own).fetch_optional(&mut **tx).await?;
    author.ok_or_else(Error::missing)
}

pub async fn list(
    State(app): State<App>,
    Actor(actor): Actor,
    Query(query): Query<PageQuery>,
) -> Result<Json<Value>> {
    let (time, id) = query.cursor()?;
    let q = query.q.as_deref().unwrap_or("").trim();
    if q.chars().count() > 200 {
        return Err(Error::bad("搜索词过长"));
    }
    let mut rows: Vec<(Uuid, DateTime<Utc>)> = sqlx::query_as(r#"SELECT p.id,p.created_at FROM posts p
        WHERE NOT p.deleted AND (p.visibility='public' OR p.author_id=$1)
        AND ($2::text IS NULL OR p.author_id=$2)
        AND ($3::timestamptz IS NULL OR (p.created_at,p.id)<($3,$4))
        AND ($5='' OR strpos(lower(p.text),lower($5))>0 OR strpos(lower(COALESCE(p.location,'')),lower($5))>0
        OR strpos(lower(COALESCE(p.location_address,'')),lower($5))>0
        OR EXISTS (SELECT 1 FROM comments c WHERE c.post_id=p.id AND NOT c.deleted AND strpos(lower(c.text),lower($5))>0))
        ORDER BY p.created_at DESC,p.id DESC LIMIT $6"#)
        .bind(&actor).bind(&query.author_id).bind(time).bind(id).bind(q).bind(query.limit()+1).fetch_all(&app.db).await?;
    let more = rows.len() > query.limit() as usize;
    rows.truncate(query.limit() as usize);
    let next = rows
        .last()
        .filter(|_| more)
        .map(|(id, time)| cursor(*time, *id));
    let mut items = Vec::new();
    for (id, _) in rows {
        // 翻页期间被删除/设为私密的行跳过，不能把旧权限下的正文泄露出去。
        match value(&app.db, &actor, id).await {
            Ok(post) => items.push(post),
            Err(e) if e.0 == StatusCode::NOT_FOUND => (),
            Err(e) => return Err(e),
        }
    }
    Ok(Json(json!({"items":items,"next_cursor":next})))
}

pub async fn get(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
) -> Result<Json<Value>> {
    Ok(Json(value(&app.db, &actor, id).await?))
}

#[derive(Deserialize, Serialize)]
#[serde(deny_unknown_fields)]
pub struct NewPost {
    pub request_id: Uuid,
    #[serde(default)]
    pub text: String,
    #[serde(default)]
    pub media_ids: Vec<Uuid>,
    pub location: Option<String>,
    pub location_address: Option<String>,
    pub visibility: String,
}

fn visibility(s: &str) -> Result<()> {
    if !matches!(s, "public" | "private") {
        return Err(Error::bad("可见性只能是公开或私密"));
    }
    Ok(())
}
pub fn conflict() -> Error {
    Error(
        StatusCode::CONFLICT,
        "该请求已处理，请使用新的请求 ID 提交修改",
    )
}

pub async fn create(
    State(app): State<App>,
    Actor(actor): Actor,
    Json(mut input): Json<NewPost>,
) -> Result<Json<Value>> {
    input.text = input.text.trim().to_owned();
    visibility(&input.visibility)?;
    if input.text.chars().count() > 5000
        || (input.text.is_empty() && input.media_ids.is_empty())
        || input.media_ids.len() > 9
    {
        return Err(Error::bad(
            "请输入正文或图片，正文最多 5000 字、图片最多 9 张",
        ));
    }
    if input
        .media_ids
        .iter()
        .collect::<std::collections::HashSet<_>>()
        .len()
        != input.media_ids.len()
    {
        return Err(Error::bad("不能重复添加同一张图片"));
    }
    if input
        .location
        .as_ref()
        .is_some_and(|s| s.chars().count() > 200)
        || input
            .location_address
            .as_ref()
            .is_some_and(|s| s.chars().count() > 500)
    {
        return Err(Error::bad("位置描述过长"));
    }
    let body = serde_json::to_value(&input).unwrap();
    let mut tx = app.db.begin().await?;
    let inserted: Option<Uuid> = sqlx::query_scalar("INSERT INTO posts (id,author_id,text,location,location_address,visibility,request_id,request_body) VALUES ($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT (author_id,request_id) DO NOTHING RETURNING id")
        .bind(Uuid::new_v4()).bind(&actor).bind(&input.text).bind(&input.location).bind(&input.location_address).bind(&input.visibility).bind(input.request_id).bind(&body).fetch_optional(&mut *tx).await?;
    let id = if let Some(id) = inserted {
        for (position, media_id) in input.media_ids.iter().enumerate() {
            media::require_owned(&mut tx, &actor, *media_id).await?;
            sqlx::query("INSERT INTO post_media (post_id,media_id,position) VALUES ($1,$2,$3)")
                .bind(id)
                .bind(media_id)
                .bind(position as i16)
                .execute(&mut *tx)
                .await?;
        }
        id
    } else {
        let (id, old, deleted): (Uuid, Value, bool) = sqlx::query_as(
            "SELECT id,request_body,deleted FROM posts WHERE author_id=$1 AND request_id=$2",
        )
        .bind(&actor)
        .bind(input.request_id)
        .fetch_one(&mut *tx)
        .await?;
        if deleted {
            return Err(Error::missing());
        }
        if old != body {
            return Err(conflict());
        }
        id
    };
    tx.commit().await?;
    Ok(Json(value(&app.db, &actor, id).await?))
}

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Visibility {
    visibility: String,
}
pub async fn update(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
    Json(input): Json<Visibility>,
) -> Result<Json<Value>> {
    visibility(&input.visibility)?;
    let mut tx = app.db.begin().await?;
    lock_visible(&mut tx, &actor, id, true).await?;
    sqlx::query("UPDATE posts SET visibility=$2 WHERE id=$1")
        .bind(id)
        .bind(input.visibility)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    Ok(Json(value(&app.db, &actor, id).await?))
}
pub async fn delete(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
) -> Result<StatusCode> {
    let mut tx = app.db.begin().await?;
    // DELETE 重试对本人已删除的行仍然成功。
    let found: Option<bool> =
        sqlx::query_scalar("SELECT deleted FROM posts WHERE id=$1 AND author_id=$2 FOR UPDATE")
            .bind(id)
            .bind(&actor)
            .fetch_optional(&mut *tx)
            .await?;
    if found.is_none() {
        return Err(Error::missing());
    }
    for statement in [
        "DELETE FROM notifications WHERE post_id=$1",
        "DELETE FROM likes WHERE post_id=$1",
        "DELETE FROM comments WHERE post_id=$1",
        "DELETE FROM post_media WHERE post_id=$1",
    ] {
        sqlx::query(statement).bind(id).execute(&mut *tx).await?;
    }
    sqlx::query("UPDATE posts SET deleted=true,text='',location=NULL,location_address=NULL,request_body='{}'::jsonb WHERE id=$1").bind(id).execute(&mut *tx).await?;
    tx.commit().await?;
    Ok(StatusCode::NO_CONTENT)
}
