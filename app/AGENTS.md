<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-06-16 -->

# app

## Purpose
Android 应用壳层模块，仅包含启动页 `SplashActivity`、主界面 `MainActivity`、`AboutActivity`、`LicensesActivity` 和最小化的 Android 平台配置。所有 UI 和业务逻辑均来自 `:core` 模块。

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | Android 应用模块构建配置 |
| `src/main/kotlin/plus/rua/project/MainActivity.kt` | 主界面 Activity，设置 `CalendarMonthView()` Composable |
| `src/main/kotlin/plus/rua/project/SplashActivity.kt` | 启动页 Activity，400ms 后跳转 MainActivity |
| `src/main/kotlin/plus/rua/project/AboutActivity.kt` | 关于页面 Activity |
| `src/main/kotlin/plus/rua/project/LicensesActivity.kt` | 许可证列表 Activity |
| `src/main/AndroidManifest.xml` | Android 清单，声明 Activities 和主题 |
| `src/main/res/values/themes.xml` | 应用主题配置（Material 3） |
| `src/main/res/values-night/themes.xml` | 夜间模式主题 |
| `src/main/res/values/strings.xml` | 应用名称字符串 |
| `src/main/res/anim/` | Activity 转场动画（slide_in/slide_out） |
| `src/main/assets/gifs/` | GIF 动画资源目录 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/` | 主源码与资源（见 `src/main/AGENTS.md`） |
| `src/debug/` | Debug 构建资源 |
| `src/release/` | Release 构建资源 |

## For AI Agents

### Working In This Directory
- 不要在此模块添加业务逻辑；所有代码应放在 `:core` 模块
- 仅修改 Android 特有的配置：Manifest、主题、权限、Activity 声明
- `MainActivity.kt` 应保持简洁，仅负责调用 `CalendarMonthView()`
- `SplashActivity.kt` 也是薄壳，将启动页 UI 委托给 `:core` 模块

### Testing Requirements
- 构建验证：`./gradlew :app:assembleDebug`
- 安装验证：`./gradlew :app:installDebug`

### Common Patterns
- 使用 `enableEdgeToEdge()` 实现全屏边缘到边缘显示
- Activity 转场使用 `overridePendingTransition()` 配合 `res/anim/` 中的 XML 动画
- 主题继承自 `Theme.AppCompat.DayNight.NoActionBar`

## Dependencies

### Internal
- `:core` 模块 — 提供所有 UI 和逻辑

### External
- Android Gradle Plugin 9.2.1
- Material 3, Compose runtime, Compose UI tooling

<!-- MANUAL: -->
