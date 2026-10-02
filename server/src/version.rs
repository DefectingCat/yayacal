use axum::http::{HeaderName, HeaderValue};
use tower_http::set_header::SetResponseHeaderLayer;

pub const VERSION: &str = concat!(
    "v",
    env!("CARGO_PKG_VERSION"),
    "-",
    env!("YAYA_BUILD_GIT_HASH")
);
pub const SERVER_ID: &str = concat!(
    "yaya server v",
    env!("CARGO_PKG_VERSION"),
    "-",
    env!("YAYA_BUILD_GIT_HASH")
);

/// 在连接数据库前输出版本，即使启动随后失败也能识别实际构建。
pub fn log_startup() {
    tracing::info!("{SERVER_ID}");
}

/// 在所有路由添加完成后应用，以覆盖健康检查、错误及默认 fallback。
pub fn header_layer() -> SetResponseHeaderLayer<HeaderValue> {
    SetResponseHeaderLayer::overriding(
        HeaderName::from_static("x-server"),
        HeaderValue::from_static(SERVER_ID),
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use axum::{
        Router,
        body::Body,
        http::{Request, StatusCode},
        routing::get,
    };
    use std::{
        io::{self, Write},
        sync::{Arc, Mutex},
    };
    use tower::ServiceExt;

    #[tokio::test]
    async fn header_covers_success_errors_head_and_fallback() {
        let router = Router::new()
            .route("/health", get(|| async { "ok" }))
            .route("/unauthorized", get(|| async { StatusCode::UNAUTHORIZED }))
            .route(
                "/failure",
                get(|| async { StatusCode::INTERNAL_SERVER_ERROR }),
            )
            .layer(header_layer());
        for (method, path, status) in [
            ("GET", "/health", StatusCode::OK),
            ("HEAD", "/health", StatusCode::OK),
            ("GET", "/unauthorized", StatusCode::UNAUTHORIZED),
            ("GET", "/failure", StatusCode::INTERNAL_SERVER_ERROR),
            ("GET", "/missing", StatusCode::NOT_FOUND),
            ("HEAD", "/missing", StatusCode::NOT_FOUND),
            ("POST", "/health", StatusCode::METHOD_NOT_ALLOWED),
        ] {
            let request = Request::builder()
                .method(method)
                .uri(path)
                .body(Body::empty())
                .unwrap();
            let response = router.clone().oneshot(request).await.unwrap();
            assert_eq!(response.status(), status, "{method} {path}");
            assert_eq!(response.headers()["x-server"], SERVER_ID, "{method} {path}");
        }
    }

    #[derive(Clone)]
    struct LogBuffer(Arc<Mutex<Vec<u8>>>);

    impl Write for LogBuffer {
        fn write(&mut self, bytes: &[u8]) -> io::Result<usize> {
            self.0.lock().unwrap().write(bytes)
        }

        fn flush(&mut self) -> io::Result<()> {
            Ok(())
        }
    }

    #[test]
    fn startup_log_matches_response_identity() {
        let buffer = LogBuffer(Arc::default());
        let writer = buffer.clone();
        let subscriber = tracing_subscriber::fmt()
            .with_ansi(false)
            .without_time()
            .with_writer(move || writer.clone())
            .finish();
        tracing::subscriber::with_default(subscriber, log_startup);
        let log = String::from_utf8(buffer.0.lock().unwrap().clone()).unwrap();
        assert!(log.contains(SERVER_ID), "{log}");
    }
}
