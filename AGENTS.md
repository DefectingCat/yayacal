<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# YaYa

## Purpose
YaYa 是一款基于纯 Android + Jetpack Compose 构建的日历应用。应用功能包括农历显示、节气标注、节假日信息、个人班次排期（WORK/OFF 循环）以及月/周/年三种视图切换。

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | 根项目 Gradle 构建设置 |
| `settings.gradle.kts` | Gradle 项目包含模块声明（`:app`, `:core`） |
| `gradle.properties` | Gradle 构建设置与缓存配置 |
| `gradle/libs.versions.toml` | 版本目录（依赖版本统一管理） |
| `CLAUDE.md` | 项目开发指南与架构文档 |
| `CHANGELOG.md` | 版本变更历史 |
| `DEVELOPMENT.md` | 性能追踪与开发工具说明 |
| `COMMENTS.md` | KDoc 注释规范 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `app/` | Android 应用壳层模块（见 `app/AGENTS.md`） |
| `core/` | Android Library 核心模块：所有 Compose UI、ViewModel 和业务逻辑（见 `core/AGENTS.md`） |
| `macrobenchmark/` | 宏基准测试模块，Baseline Profile 生成（见 `macrobenchmark/AGENTS.md`） |
| `gradle/` | Gradle Wrapper 文件（见 `gradle/wrapper/AGENTS.md`） |
| `scripts/` | 性能追踪脚本（见 `scripts/AGENTS.md`） |
| `logs/` | 性能追踪日志输出目录（见 `logs/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 所有依赖版本在 `gradle/libs.versions.toml` 中声明
- 修改依赖后需同步 Gradle
- 构建配置在根 `build.gradle.kts` 和各模块 `build.gradle.kts` 中

### Testing Requirements
- 核心模块测试：`./gradlew :core:test`
- Android 构建：`./gradlew :app:assembleDebug`
- 安装验证：`./gradlew :app:installDebug`

### Common Patterns
- Kotlin 包名统一为 `plus.rua.project`
- UI 组件在 `plus.rua.project.ui` 包下
- `:app` 模块仅包含入口 Activity，所有 UI 和逻辑在 `:core` 模块

## Dependencies

### External
- Kotlin 2.3.21, Jetpack Compose BOM, Material 3
- `kotlinx-datetime` 0.8.0, `tyme4kt`（农历/节气）, `sketch` 4.4.0（GIF）
- AGP 9.2.1, compileSdk/targetSdk 37, minSdk 24

<!-- MANUAL: -->
