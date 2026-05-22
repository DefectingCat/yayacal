<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# main

## Purpose
Android 应用主 source set，包含入口 Activities、应用清单、主题资源、转场动画和图标资源。

## Key Files

| File | Description |
|------|-------------|
| `kotlin/plus/rua/project/MainActivity.kt` | Android 入口 Activity |
| `kotlin/plus/rua/project/AboutActivity.kt` | 关于页面 Activity |
| `kotlin/plus/rua/project/LicensesActivity.kt` | 许可证列表 Activity |
| `AndroidManifest.xml` | Android 应用清单 |
| `res/values/themes.xml` | Material 3 主题定义 |
| `res/values-night/themes.xml` | 夜间模式主题 |
| `res/values/strings.xml` | 应用名称等字符串 |
| `res/anim/slide_in_right.xml` | Activity 进入动画（右滑入） |
| `res/anim/slide_out_left.xml` | Activity 退出动画（左滑出） |
| `assets/gifs/` | GIF 动画资源 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `kotlin/plus/rua/project/` | Kotlin 源码（见 `kotlin/plus/rua/project/AGENTS.md`） |
| `res/` | Android 资源文件（图标、主题、字符串、动画） |
| `res/anim/` | Activity 转场动画 XML（见 `res/anim/AGENTS.md`） |
| `res/values/` | 字符串和主题定义（见 `res/values/AGENTS.md`） |
| `assets/` | 原始资产文件（GIF 等）（见 `assets/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 仅放置 Android 平台特有的配置和入口代码
- 不要在此添加业务逻辑
- 主题和颜色配置在 `res/values/` 中
- 新增 Activity 需在 `AndroidManifest.xml` 中声明

## Dependencies

### Internal
- `:core` 模块 — Activities 调用 `:core` 中的 Composable

<!-- MANUAL: -->
