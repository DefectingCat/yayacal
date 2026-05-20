<!-- Parent: ../../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# composeResources

## Purpose
Compose Multiplatform 共享资源目录，存放跨平台可访问的图片和文件资源。

## Key Files

无（目录仅包含子目录）

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `drawable/` | 图片资源（图标、插图等） |
| `files/` | 原始文件资源 |

## For AI Agents

### Working In This Directory
- 资源通过 Compose 的 `Res` 对象访问（如 `Res.drawable.xxx`）
- 添加新资源后需重新编译以生成访问代码

<!-- MANUAL: -->
