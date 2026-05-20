<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# commonMain

## Purpose
KMP 共享核心模块，包含所有 Compose UI、ViewModel 状态管理、业务逻辑和跨平台接口声明。这是 YaYa 应用的主体代码所在。

## Key Files

| File | Description |
|------|-------------|
| `kotlin/plus/rua/project/App.kt` | 应用根 Composable，提供 Material 主题和导航入口 |
| `kotlin/plus/rua/project/AppInfo.kt` | 应用元数据（名称、版本、作者等） |
| `kotlin/plus/rua/project/CalendarViewModel.kt` | 日历状态管理（选中日期、展开/折叠、月/周/年视图） |
| `kotlin/plus/rua/project/ComposeTrace.kt` | `expect` 声明的 Trace 标记 API |
| `kotlin/plus/rua/project/Platform.kt` | `expect` 声明的平台接口 |
| `kotlin/plus/rua/project/ShiftPattern.kt` | 个人班次排期模型（WORK/OFF 循环） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `kotlin/plus/rua/project/` | 业务逻辑和根 Composable（见 `kotlin/plus/rua/project/AGENTS.md`） |
| `kotlin/plus/rua/project/ui/` | UI 组件层（见 `kotlin/plus/rua/project/ui/AGENTS.md`） |
| `composeResources/` | Compose 多平台资源（图片、文件）（见 `composeResources/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 所有新功能代码优先放在 `commonMain` 以保持跨平台兼容
- 仅在需要平台 API 时使用 `expect/actual` 模式
- `CalendarViewModel.kt` 是核心状态枢纽，修改需谨慎

### Testing Requirements
- 修改后运行 `./gradlew :shared:allTests`
- 状态逻辑测试在 `commonTest` 中

### Common Patterns
- `@Suppress("DEPRECATION")` 用于 `monthNumber` 访问时需加解释注释
- 公共 `@Composable` 函数需要 KDoc（见 `COMMENTS.md`）
- `Modifier` 参数始终放在签名最后
- 回调参数使用 `on` 前缀（`onDateClick`、`onMonthChanged`）
- UI 文本使用中文

## Dependencies

### External
- Compose Multiplatform, Material 3
- `kotlinx-datetime`, `kotlinx-coroutines`
- `tyme4kt`（农历/节气/节日）
- `sketch`（GIF 显示）
- `androidx-lifecycle-viewmodel-compose`

<!-- MANUAL: -->
