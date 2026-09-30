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

已有本地镜像时可使用 `docker compose up -d --no-build` 启动。若拉取基础镜像提示本机代理连接失败，需要修正 Docker Desktop 的代理配置，不必降级依赖。运行镜像使用非 root 用户，数据库和图片分别使用持久化卷；`/health` 同时检查数据库连通性，容器停止时处理 SIGTERM 并等待在途请求。

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
| OkHttp（Android 网络） | 5.5.0 | https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml |
| Sketch（含 HTTP 图片加载） | 4.7.0 | https://repo.maven.apache.org/maven2/io/github/panpf/sketch4/sketch-http-okhttp/maven-metadata.xml |
| ZoomImage | 1.7.0 | https://repo.maven.apache.org/maven2/io/github/panpf/zoomimage/zoomimage-compose-sketch4/maven-metadata.xml |
| org.json（仅 JVM 测试） | 20260814 | https://repo.maven.apache.org/maven2/org/json/json/maven-metadata.xml |

只更新本次新增或直接使用的依赖，不升级无关 Android 构建工具。

## Android 连接

在「工具 → 朋友圈 → 连接设置」填写服务根地址（不含 `/api/v1`）。Release 只接受 HTTPS；Debug 可以连接本地 HTTP，默认模拟器地址为 `http://10.0.2.2:8088`。

本机地址无法连通时，使用单个模拟器和 ADB 转发：

```sh
adb -s emulator-5554 reverse tcp:8088 tcp:8088
```

随后在连接设置中填 `http://127.0.0.1:8088`。USB 真机也可以用相应设备序列号转发。前台页面每 15 秒刷新，退出页面停止轮询；多页浏览时保留当前列表，通过刷新获取最新数据。消息是应用内通知，首版不含后台推送。

资料、动态、点赞、评论、通知均以服务器为准。草稿按账号和服务地址隔离，上传失败和进程重建后保留内容及请求 ID；临时媒体过期后重试会重新上传。原本 SharedPreferences 中的动态和图片不自动删除、不自动导入，因为历史动态没有可靠作者信息。

## 接口约定

除账号列表和健康检查外，JSON 接口必须带 `X-Account-ID: xiaobai` 或 `xiaojimao`。
图片供 Android 图片加载器使用，`GET /api/v1/media/{id}?account_id=xiaobai&thumbnail=true` 同样校验可见性；账号参数不是密钥。原图省略 thumbnail 参数。

| 路径（前缀 `/api/v1`） | 方法 | 用途 |
| --- | --- | --- |
| `/accounts` | GET | 两个固定账号及各自头像、封面 ID |
| `/me` | PATCH | 更新 avatar_id 或 cover_id，图片必须属于当前账号 |
| `/media` | POST | multipart，单张 JPEG/PNG/WebP，最多 10 MiB、边长 12000 像素，解码内存受限 |
| `/posts` | GET / POST | 分页查询 / 发布 |
| `/posts/{id}` | GET / PATCH / DELETE | 详情 / 修改 visibility / 删除本人动态 |
| `/posts/{id}/like` | PUT / DELETE | 幂等点赞 / 取消 |
| `/posts/{id}/comments` | GET / POST | 评论分页 / 评论与回复 |
| `/comments/{id}` | DELETE | 删除本人评论，保留空墓碑以保持回复关系 |
| `/notifications` | GET / DELETE | 消息列表 / 清空当前账号消息 |
| `/notifications/read` | POST | `{ "ids": [...] }` 标记实际读到的消息，最多 100 个 |
| `/notifications/{id}` | DELETE | 删除当前账号的某条消息 |

分页响应 `{items, next_cursor}`，通知另含 `unread_count`。`limit` 默认 20、最大 50，游标原样回传。
动态支持 `author_id` 和 `q`（正文、位置、评论）筛选；时间倒序，评论时间正序。

发布正文：`{request_id, text, media_ids: [], visibility: "public"|"private", location?, location_address?}`，最多 5000 字、9 张图片。评论：`{request_id, text, media_id?, reply_to_id?}`，最多 2000 字。`request_id` 是客户端 UUID，同一请求的重试必须复用 ID 和内容；更换内容须换 ID，否则返回 409。删除过的请求重试返回 404，不重新创建。

所有时间为服务端生成的 Unix 毫秒。动态列表携带最近三条评论预览；完整评论通过评论分页接口读取。自己操作不产生自己的通知，重复点赞/评论不重复通知。取消点赞后不再显示相应提醒；删帖、私密化后列表、详情、搜索、消息及媒体重新检查访问条件。

取消点赞后再次点赞视为新的互动，生成一条新的未读提醒；同一次点赞的网络重试不会重复提醒。

删除动态清空内容与关联记录，保留 ID 墓碑用于防重；未引用图片宽限 24 小时后由每小时运行的清理任务删除。图片不直接作为公开静态目录暴露。

## 验证

```sh
cargo fmt --check
cargo clippy --locked -- -D warnings
bash tests/run.sh
```

集成检查启动独立 PostgreSQL 18.6 容器和临时媒体目录，结束自动清理；覆盖双账号互动、并发幂等、评论回复关联、搜索分页、图片归属、私密访问和通知隔离。不要把 `tests/api.py` 指向生产服务。

2026-09-30 本地验收：369 项 Android JVM 测试通过，Debug 安装与 Release 构建通过；Rust 格式和 Clippy 检查通过，5 项真实 PostgreSQL/HTTP 集成测试在本机进程及 Linux 容器内均通过。模拟器实际验证了图文发布、另一账号点赞评论、通知与回复、私密切换和个人搜索。日常验收只启动一台模拟器，通过切换账号检查，避免同时占用多台模拟器资源。数据库恢复与媒体文件哈希校验通过。这些是本地结果，GitHub CI 和公网部署另行执行。

## 备份与恢复

需要同时备份数据库和 media 卷。在一致性备份窗口先停止 api（`docker compose stop api`），通过 `docker compose exec -T db pg_dump -U moments -d moments -Fc` 导出数据库，并备份 media 卷，再启动 api。恢复时也停止 api，将同一份备份中的数据库及媒体恢复后再启动；只恢复数据库无法找回图片。生产卷不使用 `docker compose down -v`。
