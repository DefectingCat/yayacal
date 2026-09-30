use crate::{
    App,
    accounts::Actor,
    error::{Error, Result},
    media,
    posts::{self, PageQuery},
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

async fn notify(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    recipient: &str,
    actor: &str,
    post: Uuid,
    comment: Option<Uuid>,
    kind: &str,
    key: &str,
) -> Result<()> {
    if recipient != actor {
        sqlx::query("INSERT INTO notifications (id,recipient_id,actor_id,post_id,comment_id,kind,event_key) VALUES ($1,$2,$3,$4,$5,$6,$7) ON CONFLICT (recipient_id,event_key) DO NOTHING")
            .bind(Uuid::new_v4()).bind(recipient).bind(actor).bind(post).bind(comment).bind(kind).bind(key).execute(&mut **tx).await?;
    }
    Ok(())
}
pub async fn like(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
) -> Result<StatusCode> {
    let mut tx = app.db.begin().await?;
    let author = posts::lock_visible(&mut tx, &actor, id, false).await?;
    let inserted =
        sqlx::query("INSERT INTO likes (post_id,account_id) VALUES ($1,$2) ON CONFLICT DO NOTHING")
            .bind(id)
            .bind(&actor)
            .execute(&mut *tx)
            .await?;
    // 重试已有的赞不产生消息；取消后重新插入则是一次新的互动。
    if inserted.rows_affected() != 0 {
        notify(
            &mut tx,
            &author,
            &actor,
            id,
            None,
            "like",
            &format!("like:{}", Uuid::new_v4()),
        )
        .await?;
    }
    tx.commit().await?;
    Ok(StatusCode::NO_CONTENT)
}
pub async fn unlike(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
) -> Result<StatusCode> {
    let mut tx = app.db.begin().await?;
    posts::lock_visible(&mut tx, &actor, id, false).await?;
    sqlx::query("DELETE FROM likes WHERE post_id=$1 AND account_id=$2")
        .bind(id)
        .bind(&actor)
        .execute(&mut *tx)
        .await?;
    sqlx::query(
        "UPDATE notifications SET dismissed=true WHERE post_id=$1 AND actor_id=$2 AND kind='like'",
    )
    .bind(id)
    .bind(&actor)
    .execute(&mut *tx)
    .await?;
    tx.commit().await?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct NewComment {
    request_id: Uuid,
    #[serde(default)]
    text: String,
    media_id: Option<Uuid>,
    reply_to_id: Option<Uuid>,
}
pub async fn comment(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
    Json(mut input): Json<NewComment>,
) -> Result<Json<Value>> {
    input.text = input.text.trim().to_owned();
    if input.text.chars().count() > 2000 || (input.text.is_empty() && input.media_id.is_none()) {
        return Err(Error::bad("请输入评论或图片，评论最多 2000 字"));
    }
    let mut tx = app.db.begin().await?;
    let author = posts::lock_visible(&mut tx, &actor, id, false).await?;
    let body = json!({"post_id":id,"input":input});
    let old: Option<(Uuid, Value, bool)> = sqlx::query_as(
        "SELECT id,request_body,deleted FROM comments WHERE author_id=$1 AND request_id=$2",
    )
    .bind(&actor)
    .bind(input.request_id)
    .fetch_optional(&mut *tx)
    .await?;
    if let Some((id, old, deleted)) = old {
        if deleted {
            return Err(Error::missing());
        }
        if old != body {
            return Err(posts::conflict());
        }
        return Ok(Json(json!({"id":id})));
    }
    let reply_author = if let Some(reply_id) = input.reply_to_id {
        Some(
            sqlx::query_scalar::<_, String>(
                "SELECT author_id FROM comments WHERE id=$1 AND post_id=$2 AND NOT deleted",
            )
            .bind(reply_id)
            .bind(id)
            .fetch_optional(&mut *tx)
            .await?
            .ok_or_else(|| Error::bad("被回复评论不存在或不属于这条动态"))?,
        )
    } else {
        None
    };
    if let Some(media_id) = input.media_id {
        media::require_owned(&mut tx, &actor, media_id).await?;
    }
    let comment_id = Uuid::new_v4();
    let inserted=sqlx::query("INSERT INTO comments (id,post_id,author_id,text,media_id,reply_to_id,request_id,request_body) VALUES ($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT (author_id,request_id) DO NOTHING")
        .bind(comment_id).bind(id).bind(&actor).bind(&input.text).bind(input.media_id).bind(input.reply_to_id).bind(input.request_id).bind(&body).execute(&mut *tx).await?;
    if inserted.rows_affected() == 0 {
        return Err(posts::conflict());
    }
    // 回复的接收人优先；动态作者同时收到一次评论提醒，接收人相同则按事件键去重。
    let key = format!("comment:{comment_id}");
    if let Some(recipient) = reply_author {
        notify(
            &mut tx,
            &recipient,
            &actor,
            id,
            Some(comment_id),
            "reply",
            &key,
        )
        .await?;
    }
    notify(
        &mut tx,
        &author,
        &actor,
        id,
        Some(comment_id),
        "comment",
        &key,
    )
    .await?;
    tx.commit().await?;
    Ok(Json(json!({"id":comment_id})))
}

pub async fn comments(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
    Query(query): Query<PageQuery>,
) -> Result<Json<Value>> {
    let (time, cursor) = query.cursor()?;
    // SQL 仅拼接本模块的常量片段，所有请求数据使用 bind。
    let sql = format!(
        r#"SELECT {json},c.created_at,c.id FROM comments c JOIN posts p ON p.id=c.post_id
        JOIN accounts a ON a.id=c.author_id LEFT JOIN comments r ON r.id=c.reply_to_id LEFT JOIN accounts ra ON ra.id=r.author_id
        WHERE p.id=$1 AND NOT p.deleted AND (p.visibility='public' OR p.author_id=$2)
        AND ($3::timestamptz IS NULL OR (c.created_at,c.id)>($3,$4)) ORDER BY c.created_at,c.id LIMIT $5"#,
        json = posts::COMMENT_JSON
    );
    // 先检查详情以区分“无评论”和“不可见”；实际 SELECT 仍重复权限条件避免竞态泄漏。
    posts::value(&app.db, &actor, id).await?;
    let mut rows: Vec<(Value, DateTime<Utc>, Uuid)> = sqlx::query_as(sqlx::AssertSqlSafe(sql))
        .bind(id)
        .bind(actor)
        .bind(time)
        .bind(cursor)
        .bind(query.limit() + 1)
        .fetch_all(&app.db)
        .await?;
    let more = rows.len() > query.limit() as usize;
    rows.truncate(query.limit() as usize);
    let next = rows
        .last()
        .filter(|_| more)
        .map(|(_, time, id)| posts::cursor(*time, *id));
    Ok(Json(
        json!({"items":rows.into_iter().map(|(v,_,_)|v).collect::<Vec<_>>(),"next_cursor":next}),
    ))
}

pub async fn delete_comment(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
) -> Result<StatusCode> {
    let mut tx = app.db.begin().await?;
    let post_id: Uuid =
        sqlx::query_scalar("SELECT post_id FROM comments WHERE id=$1 AND author_id=$2")
            .bind(id)
            .bind(&actor)
            .fetch_optional(&mut *tx)
            .await?
            .ok_or_else(Error::missing)?;
    posts::lock_visible(&mut tx, &actor, post_id, false).await?;
    sqlx::query("UPDATE comments SET deleted=true,text='',media_id=NULL,request_body='{}'::jsonb WHERE id=$1").bind(id).execute(&mut *tx).await?;
    sqlx::query("UPDATE notifications SET dismissed=true WHERE comment_id=$1")
        .bind(id)
        .execute(&mut *tx)
        .await?;
    tx.commit().await?;
    Ok(StatusCode::NO_CONTENT)
}

// 消息内容始终从仍可见的动态/评论读取，私密化后不能从旧消息预览绕过权限。
const NOTICE_FROM: &str = r#"FROM notifications n JOIN posts p ON p.id=n.post_id JOIN accounts a ON a.id=n.actor_id
    LEFT JOIN comments c ON c.id=n.comment_id WHERE n.recipient_id=$1 AND NOT n.dismissed AND NOT p.deleted
    AND (p.visibility='public' OR p.author_id=$1)
    AND ((n.kind='like' AND EXISTS (SELECT 1 FROM likes l WHERE l.post_id=p.id AND l.account_id=n.actor_id))
        OR (n.kind<>'like' AND NOT c.deleted))"#;

pub async fn notifications(
    State(app): State<App>,
    Actor(actor): Actor,
    Query(query): Query<PageQuery>,
) -> Result<Json<Value>> {
    let (time, id) = query.cursor()?;
    // SQL 仅拼接本模块的常量片段，所有请求数据使用 bind。
    let sql = format!(
        r#"SELECT jsonb_build_object('id',n.id,'post_id',p.id,'author_id',a.id,'author_name',a.name,'avatar_id',a.avatar_id,
        'type',n.kind,'content',c.text,'read',n.read_at IS NOT NULL,'timestamp',(extract(epoch from n.created_at)*1000)::bigint,
        'post_text',p.text,'post_media_id',(SELECT media_id FROM post_media WHERE post_id=p.id ORDER BY position LIMIT 1)),n.created_at,n.id
        {NOTICE_FROM} AND ($2::timestamptz IS NULL OR (n.created_at,n.id)<($2,$3)) ORDER BY n.created_at DESC,n.id DESC LIMIT $4"#
    );
    let mut rows: Vec<(Value, DateTime<Utc>, Uuid)> = sqlx::query_as(sqlx::AssertSqlSafe(sql))
        .bind(&actor)
        .bind(time)
        .bind(id)
        .bind(query.limit() + 1)
        .fetch_all(&app.db)
        .await?;
    let more = rows.len() > query.limit() as usize;
    rows.truncate(query.limit() as usize);
    let next = rows
        .last()
        .filter(|_| more)
        .map(|(_, time, id)| posts::cursor(*time, *id));
    let unread: i64 = sqlx::query_scalar(sqlx::AssertSqlSafe(format!(
        "SELECT count(*) {NOTICE_FROM} AND n.read_at IS NULL"
    )))
    .bind(actor)
    .fetch_one(&app.db)
    .await?;
    Ok(Json(
        json!({"items":rows.into_iter().map(|(v,_,_)|v).collect::<Vec<_>>(),"next_cursor":next,"unread_count":unread}),
    ))
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Read {
    ids: Vec<Uuid>,
}
pub async fn read(
    State(app): State<App>,
    Actor(actor): Actor,
    Json(input): Json<Read>,
) -> Result<StatusCode> {
    if input.ids.len() > 100 {
        return Err(Error::bad("一次最多标记 100 条消息"));
    }
    sqlx::query("UPDATE notifications SET read_at=COALESCE(read_at,clock_timestamp()) WHERE recipient_id=$1 AND id=ANY($2)")
        .bind(actor).bind(input.ids).execute(&app.db).await?;
    Ok(StatusCode::NO_CONTENT)
}
pub async fn dismiss(
    State(app): State<App>,
    Actor(actor): Actor,
    Path(id): Path<Uuid>,
) -> Result<StatusCode> {
    let result =
        sqlx::query("UPDATE notifications SET dismissed=true WHERE recipient_id=$1 AND id=$2")
            .bind(actor)
            .bind(id)
            .execute(&app.db)
            .await?;
    if result.rows_affected() == 0 {
        return Err(Error::missing());
    }
    Ok(StatusCode::NO_CONTENT)
}
pub async fn clear(State(app): State<App>, Actor(actor): Actor) -> Result<StatusCode> {
    sqlx::query("UPDATE notifications SET dismissed=true WHERE recipient_id=$1")
        .bind(actor)
        .execute(&app.db)
        .await?;
    Ok(StatusCode::NO_CONTENT)
}
