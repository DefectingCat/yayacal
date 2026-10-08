use crate::{
    App,
    accounts::Actor,
    error::{Error, Result},
};
use axum::{
    Json,
    body::Body,
    extract::{Multipart, Path, Query, Request, State},
    http::{StatusCode, header},
    response::Response,
};
use image::{
    DynamicImage, GenericImageView, ImageDecoder, ImageFormat, ImageReader, imageops::FilterType,
};
use serde::Deserialize;
use serde_json::{Value, json};
use std::{io::Cursor, time::Duration};
use tower_http::services::ServeFile;
use uuid::Uuid;

const MAX_UPLOAD_BYTES: usize = 50 * 1024 * 1024;
pub const MAX_UPLOAD_BODY_BYTES: usize = MAX_UPLOAD_BYTES + 1024 * 1024;
const UPLOAD_SIZE_ERROR: &str = "图片不能超过 50 MiB";
const PREVIEW_SUFFIX: &str = ".preview.webp";
const MEDIA_SUFFIXES: [&str; 3] = [PREVIEW_SUFFIX, ".thumb.webp", ".webp"];

fn decode(data: &[u8]) -> Result<DynamicImage> {
    let mut reader = ImageReader::new(Cursor::new(data))
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
    Ok(image)
}

fn preview_size(width: u32, height: u32) -> (u32, u32) {
    let longest = width.max(height) as f64;
    let shortest = width.min(height) as f64;
    let ratio = if longest >= shortest * 3.0 {
        // 长截图保留可读宽度，另设总像素边界，不按长边压成细条。
        (960.0 / shortest).min((8_000_000.0 / (width as f64 * height as f64)).sqrt())
    } else {
        1600.0 / longest
    }
    .min(1.0);
    (
        ((width as f64 * ratio) as u32).max(1),
        ((height as f64 * ratio) as u32).max(1),
    )
}

fn encode_variant(image: &DynamicImage, lossless: bool, quality: f32) -> Result<Vec<u8>> {
    let pixels = image.to_rgba8();
    webp::Encoder::from_rgba(pixels.as_raw(), image.width(), image.height())
        .encode_simple(lossless, quality)
        .map(|encoded| encoded.to_vec())
        .map_err(|_| Error::bad("图片编码失败"))
}

fn variants(image: &DynamicImage) -> Result<(Vec<u8>, Vec<u8>)> {
    let (width, height) = preview_size(image.width(), image.height());
    let transparent =
        image.color().has_alpha() && image.pixels().any(|(_, _, pixel)| pixel[3] != 255);
    let lossless =
        transparent || image.width().max(image.height()) >= image.width().min(image.height()) * 3;
    let thumbnail = encode_variant(&image.thumbnail(480, 480), transparent, 80.0)?;
    let preview = encode_variant(
        &image.resize_exact(width, height, FilterType::Lanczos3),
        lossless,
        if lossless { 75.0 } else { 85.0 },
    )?;
    Ok((thumbnail, preview))
}

async fn write_variant(app: &App, id: Uuid, suffix: &str, bytes: &[u8]) -> Result<()> {
    let path = app.media_dir.join(format!("{id}{suffix}"));
    let temporary = app.media_dir.join(format!("{id}{suffix}.tmp"));
    let result = async {
        tokio::fs::write(&temporary, bytes).await?;
        tokio::fs::rename(&temporary, path).await?;
        Ok(())
    }
    .await;
    if result.is_err() {
        let _ = tokio::fs::remove_file(temporary).await;
    }
    result
}

/// 启动时逐张补齐旧图片，只写衍生图，原图字节保持不变；失败图片不标记为可预览。
pub async fn backfill(app: &App) -> Result<()> {
    let ids: Vec<Uuid> = sqlx::query_scalar(
        "SELECT id FROM media WHERE preview_bytes IS NULL OR thumbnail_bytes IS NULL ORDER BY id",
    )
    .fetch_all(&app.db)
    .await?;
    for id in ids {
        let result = async {
            let data = tokio::fs::read(app.media_dir.join(format!("{id}.webp"))).await?;
            let (thumbnail, preview) =
                tokio::task::spawn_blocking(move || variants(&decode(&data)?))
                    .await
                    .map_err(|_| Error::bad("图片处理失败"))??;
            write_variant(app, id, ".thumb.webp", &thumbnail).await?;
            write_variant(app, id, PREVIEW_SUFFIX, &preview).await?;
            sqlx::query("UPDATE media SET thumbnail_bytes=$2,preview_bytes=$3 WHERE id=$1")
                .bind(id)
                .bind(thumbnail.len() as i64)
                .bind(preview.len() as i64)
                .execute(&app.db)
                .await?;
            Ok::<_, Error>(())
        }
        .await;
        match result {
            Ok(()) => tracing::info!(media_id = %id, "media preview backfilled"),
            Err(error) => {
                tracing::warn!(media_id = %id, reason = error.1, "media preview unavailable")
            }
        }
    }
    Ok(())
}

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
        let image = decode(&data)?;
        let mut full = Cursor::new(Vec::new());
        // 统一编码并去掉 EXIF 等元信息；源文件名从不进入路径。
        image
            .write_to(&mut full, ImageFormat::WebP)
            .map_err(|_| Error::bad("图片编码失败"))?;
        let (thumbnail, preview) = variants(&image)?;
        Ok((
            // 请求取消时阻塞任务仍可能运行，permit 必须随任务持有到处理结束。
            permit,
            image.width(),
            image.height(),
            full.into_inner(),
            thumbnail,
            preview,
        ))
    })
    .await
    .map_err(|_| Error::bad("图片处理失败"))??;
    let (_permit, width, height, full, thumbnail, preview) = processed;
    let id = Uuid::new_v4();
    tokio::fs::write(app.media_dir.join(format!("{id}.webp")), &full).await?;
    write_variant(&app, id, ".thumb.webp", &thumbnail).await?;
    write_variant(&app, id, PREVIEW_SUFFIX, &preview).await?;
    sqlx::query("INSERT INTO media (id,owner_id,width,height,bytes,thumbnail_bytes,preview_bytes) VALUES ($1,$2,$3,$4,$5,$6,$7)")
        .bind(id)
        .bind(actor)
        .bind(width as i32)
        .bind(height as i32)
        .bind(full.len() as i64)
        .bind(thumbnail.len() as i64)
        .bind(preview.len() as i64)
        .execute(&app.db)
        .await?;
    Ok(Json(
        json!({"id":id,"width":width,"height":height,"bytes":full.len(),"thumbnail_bytes":thumbnail.len(),"preview_bytes":preview.len()}),
    ))
}

#[derive(Clone, Copy, Deserialize)]
#[serde(rename_all = "snake_case")]
enum Variant {
    Original,
    Thumbnail,
    Preview,
}

#[derive(Deserialize)]
pub struct Download {
    account_id: String,
    #[serde(default)]
    thumbnail: bool,
    variant: Option<Variant>,
}

pub async fn download(
    State(app): State<App>,
    Path(id): Path<Uuid>,
    Query(query): Query<Download>,
    request: Request,
) -> Result<Response> {
    if !matches!(query.account_id.as_str(), "xiaobai" | "xiaojimao") {
        return Err(Error::missing());
    }
    let sizes: Option<(Option<i64>, Option<i64>)> = sqlx::query_as(r#"SELECT m.thumbnail_bytes,m.preview_bytes FROM media m WHERE m.id=$1 AND (
        m.owner_id=$2 OR EXISTS (SELECT 1 FROM accounts a WHERE a.avatar_id=m.id OR a.cover_id=m.id)
        OR EXISTS (SELECT 1 FROM post_media pm JOIN posts p ON p.id=pm.post_id WHERE pm.media_id=m.id AND NOT p.deleted AND (p.visibility='public' OR p.author_id=$2))
        OR EXISTS (SELECT 1 FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.media_id=m.id AND NOT c.deleted AND NOT p.deleted AND (p.visibility='public' OR p.author_id=$2))
    )"#).bind(id).bind(query.account_id).fetch_optional(&app.db).await?;
    let (_, preview_bytes) = sizes.ok_or_else(Error::missing)?;
    let variant = query.variant.unwrap_or(if query.thumbnail {
        Variant::Thumbnail
    } else {
        Variant::Original
    });
    let suffix = match variant {
        Variant::Original => ".webp",
        Variant::Thumbnail => ".thumb.webp",
        Variant::Preview if preview_bytes.is_some() => PREVIEW_SUFFIX,
        Variant::Preview => return Err(Error::missing()),
    };
    // 鉴权后流式读取，HEAD 和 Range 同样经过可见性校验。
    let mut response = ServeFile::new(app.media_dir.join(format!("{id}{suffix}")))
        .try_call(request)
        .await
        .map_err(|_| Error::missing())?
        .map(Body::new);
    if response.status() == StatusCode::NOT_FOUND {
        return Err(Error::missing());
    }
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
        for suffix in MEDIA_SUFFIXES {
            let _ = tokio::fs::remove_file(app.media_dir.join(format!("{id}{suffix}"))).await;
            let _ = tokio::fs::remove_file(app.media_dir.join(format!("{id}{suffix}.tmp"))).await;
        }
    }
    // 也清理文件写成功而数据库写失败/进程退出留下的孤儿文件。
    let mut entries = tokio::fs::read_dir(&app.media_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        if !entry.file_type().await?.is_file() {
            continue;
        }
        let name = entry.file_name().to_string_lossy().to_string();
        let final_name = name.strip_suffix(".tmp").unwrap_or(&name);
        let Some(raw) = MEDIA_SUFFIXES
            .iter()
            .find_map(|suffix| final_name.strip_suffix(suffix))
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

#[cfg(test)]
mod tests {
    use super::*;
    use image::{Rgb, RgbImage, Rgba, RgbaImage};

    #[test]
    fn preview_size_preserves_small_images_and_caps_photos() {
        assert_eq!(preview_size(320, 240), (320, 240));
        assert_eq!(preview_size(4000, 3000), (1600, 1200));
        assert_eq!(preview_size(1440, 2560), (900, 1600));
    }

    #[test]
    fn preview_size_keeps_long_screenshots_readable_with_bounded_pixels() {
        assert_eq!(preview_size(1080, 8000), (960, 7111));
        assert_eq!(preview_size(8000, 1080), (7111, 960));
        let (width, height) = preview_size(2000, 12000);
        assert!(width as u64 * height as u64 <= 8_000_000);
        assert!(width >= 900);
    }

    #[test]
    fn photo_variants_use_lossy_webp_and_keep_preview_resolution() {
        let image = DynamicImage::ImageRgb8(RgbImage::from_fn(1920, 1080, |x, y| {
            Rgb([
                (x % 256) as u8,
                (y % 256) as u8,
                ((x * 7 + y * 11) % 256) as u8,
            ])
        }));
        let (thumbnail, preview) = variants(&image).unwrap_or_else(|error| panic!("{}", error.1));
        assert!(preview.windows(4).any(|tag| tag == b"VP8 "));
        let thumb = decode(&thumbnail).unwrap_or_else(|error| panic!("{}", error.1));
        let preview = decode(&preview).unwrap_or_else(|error| panic!("{}", error.1));
        assert_eq!((thumb.width(), thumb.height()), (480, 270));
        assert_eq!((preview.width(), preview.height()), (1600, 900));
    }

    #[test]
    fn transparent_preview_preserves_pixels_and_does_not_upscale() {
        let source = RgbaImage::from_pixel(8, 4, Rgba([30, 120, 200, 128]));
        let (_, preview) = variants(&DynamicImage::ImageRgba8(source.clone()))
            .unwrap_or_else(|error| panic!("{}", error.1));
        let decoded = decode(&preview).unwrap_or_else(|error| panic!("{}", error.1));
        assert_eq!(decoded.to_rgba8(), source);
        let opaque =
            DynamicImage::ImageRgba8(RgbaImage::from_pixel(8, 4, Rgba([30, 120, 200, 255])));
        let (_, preview) = variants(&opaque).unwrap_or_else(|error| panic!("{}", error.1));
        assert!(preview.windows(4).any(|tag| tag == b"VP8 "));
    }
}
