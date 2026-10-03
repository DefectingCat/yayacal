use crate::{
    App,
    accounts::Actor,
    error::{Error, Result},
};
use axum::{
    Json,
    body::Body,
    extract::{Multipart, Path, Query, State},
    http::{StatusCode, header},
    response::Response,
};
use image::{DynamicImage, ImageDecoder, ImageFormat, ImageReader};
use serde::Deserialize;
use serde_json::{Value, json};
use std::{io::Cursor, time::Duration};
use uuid::Uuid;

const MAX_UPLOAD_BYTES: usize = 50 * 1024 * 1024;
pub const MAX_UPLOAD_BODY_BYTES: usize = MAX_UPLOAD_BYTES + 1024 * 1024;
const UPLOAD_SIZE_ERROR: &str = "图片不能超过 50 MiB";

fn upload_error(error: axum::extract::multipart::MultipartError) -> Error {
    if error.status() == StatusCode::PAYLOAD_TOO_LARGE {
        Error(StatusCode::PAYLOAD_TOO_LARGE, UPLOAD_SIZE_ERROR)
    } else {
        Error::bad("上传内容无效")
    }
}

pub async fn require_owned(
    tx: &mut sqlx::Transaction<'_, sqlx::Postgres>,
    actor: &str,
    id: Uuid,
) -> Result<()> {
    // 锁住图片，防止清理任务在发布/换头像的事务提交前删除它。
    let found: Option<Uuid> =
        sqlx::query_scalar("SELECT id FROM media WHERE id=$1 AND owner_id=$2 FOR SHARE")
            .bind(id)
            .bind(actor)
            .fetch_optional(&mut **tx)
            .await?;
    if found.is_none() {
        return Err(Error::bad("图片不存在或不属于当前账号"));
    }
    Ok(())
}

pub async fn upload(
    State(app): State<App>,
    Actor(actor): Actor,
    mut multipart: Multipart,
) -> Result<Json<Value>> {
    // 在读取文件之前限制并发，避免多个请求各持有大文件及解码内存。
    let permit = app
        .media_uploads
        .clone()
        .try_acquire_owned()
        .map_err(|_| Error(StatusCode::TOO_MANY_REQUESTS, "图片上传繁忙，请稍后重试"))?;
    let field = multipart
        .next_field()
        .await
        .map_err(upload_error)?
        .ok_or_else(|| Error::bad("请选择图片"))?;
    let data = field.bytes().await.map_err(upload_error)?;
    if data.len() > MAX_UPLOAD_BYTES {
        return Err(Error(StatusCode::PAYLOAD_TOO_LARGE, UPLOAD_SIZE_ERROR));
    }
    if multipart
        .next_field()
        .await
        .map_err(upload_error)?
        .is_some()
    {
        return Err(Error::bad("每次上传一张图片"));
    }
    let processed = tokio::task::spawn_blocking(move || -> Result<_> {
        let mut reader = ImageReader::new(Cursor::new(&data))
            .with_guessed_format()
            .map_err(|_| Error::bad("无效图片"))?;
        if !matches!(
            reader.format(),
            Some(ImageFormat::Jpeg | ImageFormat::Png | ImageFormat::WebP)
        ) {
            return Err(Error::bad("仅支持 JPEG、PNG、WebP"));
        }
        let mut limits = image::Limits::default();
        limits.max_image_width = Some(12000);
        limits.max_image_height = Some(12000);
        limits.max_alloc = Some(128 * 1024 * 1024);
        reader.limits(limits);
        let mut decoder = reader
            .into_decoder()
            .map_err(|_| Error::bad("图片损坏或尺寸过大"))?;
        let orientation = decoder
            .orientation()
            .map_err(|_| Error::bad("图片方向信息损坏"))?;
        let mut image =
            DynamicImage::from_decoder(decoder).map_err(|_| Error::bad("图片损坏或尺寸过大"))?;
        image.apply_orientation(orientation);
        let mut full = Cursor::new(Vec::new());
        let mut thumb = Cursor::new(Vec::new());
        // 统一编码并去掉 EXIF 等元信息；源文件名从不进入路径。
        image
            .write_to(&mut full, ImageFormat::WebP)
            .map_err(|_| Error::bad("图片编码失败"))?;
        image
            .thumbnail(480, 480)
            .write_to(&mut thumb, ImageFormat::WebP)
            .map_err(|_| Error::bad("图片编码失败"))?;
        Ok((
            // 请求取消时阻塞任务仍可能运行，permit 必须随任务持有到处理结束。
            permit,
            image.width(),
            image.height(),
            full.into_inner(),
            thumb.into_inner(),
        ))
    })
    .await
    .map_err(|_| Error::bad("图片处理失败"))??;
    let (_permit, width, height, full, thumb) = processed;
    let id = Uuid::new_v4();
    tokio::fs::write(app.media_dir.join(format!("{id}.webp")), &full).await?;
    tokio::fs::write(app.media_dir.join(format!("{id}.thumb.webp")), thumb).await?;
    sqlx::query("INSERT INTO media (id,owner_id,width,height,bytes) VALUES ($1,$2,$3,$4,$5)")
        .bind(id)
        .bind(actor)
        .bind(width as i32)
        .bind(height as i32)
        .bind(full.len() as i64)
        .execute(&app.db)
        .await?;
    Ok(Json(json!({"id":id,"width":width,"height":height})))
}

#[derive(Deserialize)]
pub struct Download {
    account_id: String,
    #[serde(default)]
    thumbnail: bool,
}

pub async fn download(
    State(app): State<App>,
    Path(id): Path<Uuid>,
    Query(query): Query<Download>,
) -> Result<Response> {
    if !matches!(query.account_id.as_str(), "xiaobai" | "xiaojimao") {
        return Err(Error::missing());
    }
    let visible: bool = sqlx::query_scalar(r#"SELECT EXISTS (SELECT 1 FROM media m WHERE m.id=$1 AND (
        m.owner_id=$2 OR EXISTS (SELECT 1 FROM accounts a WHERE a.avatar_id=m.id OR a.cover_id=m.id)
        OR EXISTS (SELECT 1 FROM post_media pm JOIN posts p ON p.id=pm.post_id WHERE pm.media_id=m.id AND NOT p.deleted AND (p.visibility='public' OR p.author_id=$2))
        OR EXISTS (SELECT 1 FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.media_id=m.id AND NOT c.deleted AND NOT p.deleted AND (p.visibility='public' OR p.author_id=$2))
    ))"#).bind(id).bind(query.account_id).fetch_one(&app.db).await?;
    if !visible {
        return Err(Error::missing());
    }
    let suffix = if query.thumbnail {
        ".thumb.webp"
    } else {
        ".webp"
    };
    let data = tokio::fs::read(app.media_dir.join(format!("{id}{suffix}")))
        .await
        .map_err(|_| Error::missing())?;
    let mut response = Response::new(Body::from(data));
    response
        .headers_mut()
        .insert(header::CONTENT_TYPE, "image/webp".parse().unwrap());
    response
        .headers_mut()
        .insert(header::CACHE_CONTROL, "private, no-store".parse().unwrap());
    response
        .headers_mut()
        .insert(header::X_CONTENT_TYPE_OPTIONS, "nosniff".parse().unwrap());
    Ok(response)
}

pub async fn cleanup(app: &App) -> Result<()> {
    // 宽限一天供草稿重试；FK 及 require_owned 的锁保护并发绑定。
    let removed: Vec<Uuid> = sqlx::query_scalar(
        r#"DELETE FROM media m WHERE created_at < now()-interval '1 day'
        AND NOT EXISTS (SELECT 1 FROM accounts a WHERE a.avatar_id=m.id OR a.cover_id=m.id)
        AND NOT EXISTS (SELECT 1 FROM post_media pm WHERE pm.media_id=m.id)
        AND NOT EXISTS (SELECT 1 FROM comments c WHERE c.media_id=m.id)
        RETURNING id"#,
    )
    .fetch_all(&app.db)
    .await?;
    for id in removed {
        for suffix in [".webp", ".thumb.webp"] {
            let _ = tokio::fs::remove_file(app.media_dir.join(format!("{id}{suffix}"))).await;
        }
    }
    // 也清理文件写成功而数据库写失败/进程退出留下的孤儿文件。
    let mut entries = tokio::fs::read_dir(&app.media_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        if !entry.file_type().await?.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_string();
        let Some(raw) = name
            .strip_suffix(".thumb.webp")
            .or_else(|| name.strip_suffix(".webp"))
        else {
            continue;
        };
        let Ok(id) = Uuid::parse_str(raw) else {
            continue;
        };
        let old = entry
            .metadata()
            .await?
            .modified()?
            .elapsed()
            .unwrap_or_default()
            > Duration::from_secs(86400);
        if old
            && !sqlx::query_scalar::<_, bool>("SELECT EXISTS (SELECT 1 FROM media WHERE id=$1)")
                .bind(id)
                .fetch_one(&app.db)
                .await?
        {
            tokio::fs::remove_file(entry.path()).await?;
        }
    }
    Ok(())
}
