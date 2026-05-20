<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# main

## Purpose
Android 应用主 source set，包含入口 Activity、应用清单、主题资源和图标资源。

## Key Files

| File | Description |
|------|-------------|
| `kotlin/plus/rua/project/MainActivity.kt` | Android 入口 Activity |
| `AndroidManifest.xml` | Android 应用清单 |
| `res/values/themes.xml` | Material 3 主题定义 |
| `res/values-night/themes.xml` | 夜间模式主题 |
| `res/values/strings.xml` | 应用名称等字符串 |
| `assets/app_icon.png` | 应用图标 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `kotlin/plus/rua/project/` | Kotlin 源码（见 `kotlin/plus/rua/project/AGENTS.md`） |
| `res/` | Android 资源文件（图标、主题、字符串） |
| `assets/` | 原始资产文件（GIF 等） |

## For AI Agents

### Working In This Directory
- 仅放置 Android 平台特有的配置和入口代码
- 不要在此添加业务逻辑
- 主题和颜色配置在 `res/values/` 中

## Dependencies

### Internal
- `:shared` 模块 — `MainActivity` 调用 `App()` 入口

<!-- MANUAL: -->