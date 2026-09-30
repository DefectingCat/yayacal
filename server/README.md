# 鸭鸭朋友圈后端

单进程 Axum + PostgreSQL。只有 `xiaobai`（小白）和 `xiaojimao`（小鸡毛）两个固定身份，不提供注册和密码登录。请求头 `X-Account-ID` 选择身份，任何能访问接口的人都可以选择任一账号；请把服务入口部署在你信任的访问范围内。

## 启动

```sh
cd server
cp .env.example .env
# 将 POSTGRES_PASSWORD 改为随机十六进制值，例如 openssl rand -hex 24 的输出。
docker compose up -d --build
curl http://127.0.0.1:8088/health
```

容器只向本机发布 8088 端口，可由 HTTPS 反向代理或私人网络入口转发。PostgreSQL 不公开端口。数据库迁移在启动时执行，失败会停止启动；不能删除已有迁移后重新创建生产数据库。

本地运行：设置 `DATABASE_URL`、可选 `BIND_ADDR`（默认 `127.0.0.1:8088`）、`MEDIA_DIR`（默认 `data/media`），运行 `cargo run --locked`。

## 依赖核验

2026-09-30 联网核对最新稳定版，并用 Cargo.lock 锁定解析结果：

| 依赖 | 版本 | 官方来源 |
| --- | --- | --- |
| Axum | 0.8.9 | https://docs.rs/axum/latest/axum/ |
| Tokio | 1.53.1 | https://docs.rs/tokio/latest/tokio/ |
| SQLx | 0.9.0 | https://docs.rs/sqlx/latest/sqlx/ |
| Serde | 1.0.229 | https://docs.rs/serde/latest/serde/ |
| serde_json | 1.0.151 | https://docs.rs/serde_json/latest/serde_json/ |
| UUID | 1.26.1 | https://docs.rs/uuid/latest/uuid/ |
| Chrono | 0.4.45 | https://docs.rs/chrono/latest/chrono/ |
| image | 0.25.10 | https://docs.rs/image/latest/image/ |
| tracing | 0.1.44 | https://docs.rs/tracing/latest/tracing/ |
| tracing-subscriber | 0.3.23 | https://docs.rs/tracing-subscriber/latest/tracing_subscriber/ |
| tower-http | 0.7.1 | https://docs.rs/tower-http/latest/tower_http/ |
| PostgreSQL | 18.6 | https://www.postgresql.org/docs/18/release-18-6.html |
| Rust | 1.98.1 | https://static.rust-lang.org/dist/channel-rust-stable.toml |

只更新本次新增或直接使用的依赖，不升级无关 Android 构建工具。
