<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-05-22 -->

# gifs

## Purpose
GIF 动画资源目录，存放应用使用的动画 GIF 文件（001.gif ~ 152.gif），由 `AnimatedGif` Composable 组件通过 `sketch` 库加载显示。

## Key Files

| File | Description |
|------|-------------|
| `001.gif` ~ `152.gif` | 应用动画 GIF 资源 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 替换或新增 GIF 时保持连续编号
- GIF 文件较大，注意 APK 体积影响
- `AnimatedGif` 组件使用 `sketch` 库异步加载和播放

## Dependencies

### Internal
- `core/src/main/kotlin/plus/rua/project/ui/AnimatedGif.kt` — GIF 显示组件

### External
- `sketch` 4.4.0（GIF 解码和播放）

<!-- MANUAL: -->
