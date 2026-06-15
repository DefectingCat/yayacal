<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-06-15 -->

# assets

## Purpose
`:app` 模块原始资产文件目录。当前为空（2026-06 清理后）。

## Key Files

无

## Subdirectories

无（原 `gifs/` 子目录的镜像说明已删除 —— 实际 UI/WebP 资源只在 `:core`）

## For AI Agents

### Working In This Directory
- 历史上此目录曾放 `app_icon.png`/`app_icon_original.png` 作图标源，因无代码引用已于 2026-06 删除
- 应用内图标用 `core/src/main/assets/app_icon.webp`（`Platform.kt`）
- 启动器图标用 `app/src/main/res/mipmap-*/`（含 Adaptive Icon）
- **不要再把图标源 PNG 放这里**：会被打进 APK。原图 `app_icon_original.png` (2048px) 保留在 git 历史（commit `a36f6c4`），重新生成 foreground 时用 `git show a36f6c4:app/src/main/assets/app_icon_original.png` 恢复，生成脚本见 `docs/superpowers/plans/2026-06-15-image-management-optimization.md` Task 5

<!-- MANUAL: -->
