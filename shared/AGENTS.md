<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# shared

## Purpose
Kotlin Multiplatform 共享模块，包含所有 Compose UI、ViewModel 和业务逻辑。通过 `expect/actual` 机制为 Android 和 iOS 提供平台特定实现。这是项目的核心模块。

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | 共享模块构建配置（KMP 插件设置、source sets、依赖） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/androidMain/` | Android 平台特定实现（见 `src/androidMain/AGENTS.md`） |
| `src/commonMain/` | 所有 Compose UI、ViewModel 和业务逻辑（见 `src/commonMain/AGENTS.md`） |
| `src/commonTest/` | 共享测试套件（见 `src/commonTest/AGENTS.md`） |
| `src/iosMain/` | iOS 平台特定实现（见 `src/iosMain/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 所有功能代码应放在 `commonMain` 中以跨平台复用
- 仅在 `androidMain` / `iosMain` 中放置平台特有实现
- 修改 `commonMain` 后需运行 `:shared:allTests` 验证

### Testing Requirements
- 全部测试：`./gradlew :shared:allTests`
- Android 主机测试：`./gradlew :shared:testAndroidHostTest`
- 单类测试：`./gradlew :shared:testAndroidHostTest --tests "ClassName"`

### Common Patterns
- `expect` 声明在 `commonMain`，`actual` 实现在平台 source set
- Compose 资源放在 `commonMain/composeResources/` 下
- 包名：`plus.rua.project`（逻辑层）、`plus.rua.project.ui`（UI 层）

## Dependencies

### External
- Compose Multiplatform 1.11.0, Material 3 1.10.0-alpha05
- `kotlinx-datetime` 0.8.0, `kotlinx-coroutines`
- `tyme4kt`（农历/节气）, `sketch` 4.4.0（GIF 显示）
- `molecule`（测试用）

<!-- MANUAL: -->