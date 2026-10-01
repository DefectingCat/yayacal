use axum::http::{Request, Response};
use std::{io::IsTerminal, time::Duration};
use tower_http::trace::{DefaultOnResponse, OnResponse};
use tracing::{Level, Span};

/// 日志统一写入标准输出，仅在交互式终端启用颜色。
pub fn init() {
    tracing_subscriber::fmt()
        .with_writer(std::io::stdout)
        .with_ansi(std::io::stdout().is_terminal())
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "yayacal_server=info,tower_http=info".into()),
        )
        .init();
}

/// INFO span 保留请求上下文，路径省略查询参数中的搜索正文等内容。
pub fn request_span<B>(request: &Request<B>) -> Span {
    tracing::info_span!("request", method = %request.method(), path = request.uri().path())
}

#[derive(Clone)]
pub struct ResponseLogger {
    health_check: bool,
}

impl ResponseLogger {
    pub fn new(health_check: bool) -> Self {
        Self { health_check }
    }
}

impl<B> OnResponse<B> for ResponseLogger {
    fn on_response(self, response: &Response<B>, latency: Duration, span: &Span) {
        // 只降低成功健康检查的级别，失败仍保留访问摘要和默认的 ERROR 诊断。
        let level = if self.health_check && response.status().is_success() {
            Level::DEBUG
        } else {
            Level::INFO
        };
        DefaultOnResponse::new()
            .level(level)
            .on_response(response, latency, span);
    }
}
