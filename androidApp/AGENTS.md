<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# androidApp

## Purpose
Android 应用壳层模块，仅包含入口 `MainActivity` 和最小化的 Android 平台配置。所有 UI 和业务逻辑均来自 `:shared` 模块。

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | Android 应用模块构建配置 |
| `src/main/kotlin/plus/rua/project/MainActivity.kt` | Android 入口 Activity，设置 `App()` Composable |
| `src/main/AndroidManifest.xml` | Android 清单，声明 MainActivity 和主题 |
| `src/main/res/values/themes.xml` | 应用主题配置（Material 3） |
| `src/main/res/values-night/themes.xml` | 夜间模式主题 |
| `src/main/res/values/strings.xml` | 应用名称字符串 |
| `src/main/assets/app_icon.png` | 应用图标资源 |
| `src/main/assets/gifs/` | GIF 动画资源目录 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/` | 主源码与资源（见 `src/main/AGENTS.md`） |
| `src/debug/` | Debug 构建资源（如 debug 图标） |
| `src/release/` | Release 构建资源 |

## For AI Agents

### Working In This Directory
- 不要在此模块添加业务逻辑；所有代码应放在 `:shared` 模块
- 仅修改 Android 特有的配置：Manifest、主题、权限、应用图标
- `MainActivity.kt` 应保持简洁，仅负责调用 `App()`

### Testing Requirements
- 构建验证：`./gradlew :androidApp:assembleDebug`
- 安装验证：`./gradlew :androidApp:installDebug`

### Common Patterns
- 使用 `enableEdgeToEdge()` 实现全屏边缘到边缘显示
- 主题继承自 `Theme.AppCompat.DayNight.NoActionBar`

## Dependencies

### Internal
- `:shared` 模块 — 提供所有 UI 和逻辑

### External
- Android Gradle Plugin 9.2.1
- Material 3, Compose runtime

<!-- MANUAL: -->