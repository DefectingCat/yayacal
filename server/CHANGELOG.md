# 后端 Changelog

鸭鸭朋友圈后端独立版本的更新记录。Android 更新记录见 [根目录 Changelog](../CHANGELOG.md)。
基础版本遵循 Semantic Versioning；运行时版本附加七位 Git hash，例如 `v0.1.0-a1b2c3d`。

## [Unreleased]

## [0.1.0] - 2026-10-02

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

[0.1.0]: https://github.com/DefectingCat/yayacal/releases/tag/server-v0.1.0
