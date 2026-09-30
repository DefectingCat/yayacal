mod accounts;
mod error;
mod media;

use axum::{
    Router,
    extract::DefaultBodyLimit,
    routing::{get, patch, post},
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
    tracing_subscriber::fmt()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "yayacal_server=info,tower_http=info".into()),
        )
        .init();
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
    let router = Router::new()
        .route("/health", get(|| async { "ok" }))
        .route("/api/v1/accounts", get(accounts::list))
        .route("/api/v1/me", patch(accounts::update))
        .route(
            "/api/v1/media",
            post(media::upload).layer(DefaultBodyLimit::max(11 * 1024 * 1024)),
        )
        .route("/api/v1/media/{id}", get(media::download))
        .layer(TraceLayer::new_for_http())
        .with_state(app);
    let address = std::env::var("BIND_ADDR").unwrap_or_else(|_| "127.0.0.1:8088".into());
    let listener = tokio::net::TcpListener::bind(&address).await?;
    tracing::info!(%address, "moments server listening");
    axum::serve(listener, router)
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await?;
    Ok(())
}
