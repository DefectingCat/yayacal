<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-06-15 -->

# assets

## Purpose
核心模块原始资产文件目录，通过 `AssetManager` 在运行时访问。包含应用内图标和动画 WebP 资源。

## Key Files

| File | Description |
|------|-------------|
| `app_icon.webp` | 应用内展示用的图标（`Platform.kt` 的 `getAppIconUri()` 引用） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `animations/` | 动画 WebP 资源（见 `animations/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 新增原始资源文件直接放入此目录或其子目录
- **不与 `app/src/main/assets/` 镜像**：启动器图标在 `:app`，UI/WebP 资源在 `:core`
- 通过 `file:///android_asset/<path>` URI 在运行时访问

<!-- MANUAL: -->
