# 后端 Changelog

鸭鸭朋友圈后端独立版本的更新记录。Android 更新记录见 [根目录 Changelog](../CHANGELOG.md)。
基础版本遵循 Semantic Versioning；运行时版本附加七位 Git hash，例如 `v0.1.0-a1b2c3d`。

## [Unreleased]

### Added

- 1600 像素高清预览、长截图宽度保真策略、图片规格大小元数据与显式规格下载接口。
- 启动时逐张补齐旧图片衍生图，原图字节保持不变；动态配图和图片评论都报告预览可用状态。

### Changed

- 普通照片的 480 像素缩略图和高清预览使用 libwebp 有损编码，透明图片与长截图预览优先无损。
- 图片处理并发改为一张，衍生图原子写入；下载鉴权后流式返回，HEAD 和 Range 保持同样的可见性校验。
- 清理任务同时删除高清预览和写入失败遗留的临时文件，旧缩略图参数继续兼容。

### Tests

- 覆盖照片与长截图尺寸、透明度、旧图启动补齐与原图保留、规格大小、HEAD/Range、动态私密化后的所有规格权限。

## [0.1.1] - 2026-10-03

### Changed

- 单张图片上传上限由 10 MiB 提高至 50 MiB，multipart 请求体上限为 51 MiB。
- 图片上传同时最多处理两张，读取文件前获取名额；繁忙时返回 429，取消请求后正确释放名额。

### Fixed

- 超出图片或请求体上限时统一返回 413 与明确的大小提示。
- 增加真实 PostgreSQL/HTTP 集成测试，覆盖超过旧上限、恰好 50 MiB、超限、并发限制和取消后的恢复。

## [0.1.0] - 2026-10-03

### Added

- Rust Axum + PostgreSQL 朋友圈服务，提供两个固定账号的资料、动态、点赞、评论回复、通知、搜索和分页接口。
- 图片上传、缩略图、归属与可见性校验、未引用图片定时清理；数据库迁移在启动时执行。
- 发布和评论请求幂等处理、删除墓碑和通知隔离；取消点赞后再次点赞生成新的未读通知。
- 独立后端版本与 Git hash，启动日志和所有 HTTP 响应的 `X-Server` 均携带 `yaya server v版本-hash`。
- 非 root Docker 镜像、持久化数据库与媒体卷、数据库健康检查和优雅停机。
- Rust 单元测试、真实 PostgreSQL/HTTP 集成测试、Release 构建及 Docker 容器验证。
- `server-vX.Y.Z` tag 自动创建后端 GitHub Release，发布 amd64/arm64 镜像至 GHCR。

### Fixed

- 恢复默认头像操作保持账号隔离、幂等性和封面资料。
- 图片按方向信息纠正后再移除元数据。

[0.1.1]: https://github.com/DefectingCat/yayacal/releases/tag/server-v0.1.1
[0.1.0]: https://github.com/DefectingCat/yayacal/releases/tag/server-v0.1.0
