use axum::{
    Json,
    http::StatusCode,
    response::{IntoResponse, Response},
};
use serde_json::json;

pub struct Error(pub StatusCode, pub &'static str);
pub type Result<T> = std::result::Result<T, Error>;

impl Error {
    pub fn bad(message: &'static str) -> Self {
        Self(StatusCode::BAD_REQUEST, message)
    }
    pub fn missing() -> Self {
        Self(StatusCode::NOT_FOUND, "内容不存在或不可见")
    }
}
impl IntoResponse for Error {
    fn into_response(self) -> Response {
        (self.0, Json(json!({"error": self.1}))).into_response()
    }
}
impl From<sqlx::Error> for Error {
    fn from(error: sqlx::Error) -> Self {
        tracing::error!(%error, "database operation failed");
        Self(StatusCode::INTERNAL_SERVER_ERROR, "数据库操作失败，请重试")
    }
}
impl From<std::io::Error> for Error {
    fn from(error: std::io::Error) -> Self {
        tracing::error!(%error, "media operation failed");
        Self(StatusCode::INTERNAL_SERVER_ERROR, "图片读写失败，请重试")
    }
}
