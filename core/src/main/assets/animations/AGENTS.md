<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-06-15 -->

# animations

## Purpose
动画 WebP 资源目录，存放应用日历切换时随机展示的动画（文件名 `001.webp` ~ `NNN.webp`，由构建期扫描生成）。由 `AnimatedWebp` Composable 通过 `sketch` 库加载。

> 历史命名曾为 `gifs/`（2026-06 前），实际格式一直是 WebP 动画，现已正名。

## Key Files

| File | Description |
|------|-------------|
| `001.webp` ~ `NNN.webp` | 动画 WebP 资源，编号零填充三位 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 替换或新增 WebP 时保持连续三位编号（如 `153.webp`）
- **无需手动更新列表**：`core/build.gradle.kts` 在构建期扫描本目录生成 `BuildConfig.WEBP_FILES`
- 由 `AnimatedWebpFilesTest` 守卫目录与列表一致
- WebP 文件较大，注意 APK 体积

## Dependencies

### Internal
- `core/src/main/kotlin/plus/rua/project/ui/AnimatedWebp.kt` — WebP 显示组件
- `core/build.gradle.kts` — 构建期扫描注入 `BuildConfig.WEBP_FILES`

### External
- `sketch` 4.4.0（`sketch-animated-webp`，动画 WebP 解码播放）

<!-- MANUAL: -->
