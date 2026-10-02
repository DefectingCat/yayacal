mod accounts;
mod error;
mod interactions;
mod logging;
mod media;
mod posts;
mod version;

use axum::{
    Router,
    extract::{DefaultBodyLimit, State},
    routing::{delete, get, patch, post, put},
};
use sqlx::postgres::PgPoolOptions;
use std::{path::PathBuf, time::Duration};
use tower_http::trace::TraceLayer;

#[derive(Clone)]
pub struct App {
    pub db: sqlx::PgPool,
    pub media_dir: PathBuf,
}

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    logging::init();
    version::log_startup();
    let db = PgPoolOptions::new()
        .max_connections(10)
        .acquire_timeout(Duration::from_secs(10))
        .connect(&std::env::var("DATABASE_URL")?)
        .await?;
    sqlx::migrate!().run(&db).await?;
    let app = App {
        db,
        media_dir: std::env::var_os("MEDIA_DIR")
            .map(PathBuf::from)
            .unwrap_or_else(|| "data/media".into()),
    };
    tokio::fs::create_dir_all(&app.media_dir).await?;
    let cleanup = app.clone();
    tokio::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(3600));
        loop {
            interval.tick().await;
            if let Err(error) = media::cleanup(&cleanup).await {
                tracing::error!(status = %error.0, "media cleanup failed");
            }
        }
    });
    let request_trace = TraceLayer::new_for_http().make_span_with(logging::request_span);
    let router = Router::new()
        .route("/api/v1/accounts", get(accounts::list))
        .route("/api/v1/me", patch(accounts::update))
        .route("/api/v1/me/avatar", delete(accounts::reset_avatar))
        .route(
            "/api/v1/media",
            post(media::upload).layer(DefaultBodyLimit::max(11 * 1024 * 1024)),
        )
        .route("/api/v1/media/{id}", get(media::download))
        .route("/api/v1/posts", get(posts::list).post(posts::create))
        .route(
            "/api/v1/posts/{id}",
            get(posts::get).patch(posts::update).delete(posts::delete),
        )
        .route(
            "/api/v1/posts/{id}/like",
            put(interactions::like).delete(interactions::unlike),
        )
        .route(
            "/api/v1/posts/{id}/comments",
            get(interactions::comments).post(interactions::comment),
        )
        .route(
            "/api/v1/comments/{id}",
            delete(interactions::delete_comment),
        )
        .route(
            "/api/v1/notifications",
            get(interactions::notifications).delete(interactions::clear),
        )
        .route("/api/v1/notifications/read", post(interactions::read))
        .route("/api/v1/notifications/{id}", delete(interactions::dismiss))
        .layer(
            request_trace
                .clone()
                .on_response(logging::ResponseLogger::new(false)),
        )
        // Router::layer 仅影响已有路由，健康检查随后添加以避免重复记录。
        .route(
            "/health",
            get(health).layer(request_trace.on_response(logging::ResponseLogger::new(true))),
        )
        .with_state(app)
        .layer(version::header_layer());
    let address = std::env::var("BIND_ADDR").unwrap_or_else(|_| "127.0.0.1:8088".into());
    let listener = tokio::net::TcpListener::bind(&address).await?;
    tracing::info!(%address, version = version::VERSION, "moments server listening");
    axum::serve(listener, router)
        .with_graceful_shutdown(shutdown())
        .await?;
    Ok(())
}

async fn health(State(app): State<App>) -> error::Result<&'static str> {
    sqlx::query("SELECT 1").execute(&app.db).await?;
    Ok("ok")
}

async fn shutdown() {
    #[cfg(unix)]
    {
        let mut terminate =
            tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate())
                .expect("install SIGTERM handler");
        tokio::select! { _ = tokio::signal::ctrl_c() => {}, _ = terminate.recv() => {} }
    }
    #[cfg(not(unix))]
    {
        let _ = tokio::signal::ctrl_c().await;
    }
}
