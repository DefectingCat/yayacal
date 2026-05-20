<!-- Parent: ../../../../../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# project

## Purpose
共享模块的业务逻辑层，包含根 Composable、ViewModel、平台接口和班次模型。

## Key Files

| File | Description |
|------|-------------|
| `App.kt` | 应用根 Composable，设置 Material 3 主题，管理月/年视图切换和关于页面导航 |
| `AppInfo.kt` | 应用信息常量：名称、版本号、构建日期、作者、仓库地址、许可证 |
| `CalendarViewModel.kt` | 日历核心状态管理：选中日期、折叠状态、月/周/年模式、ISO 周数计算 |
| `ComposeTrace.kt` | `expect` 声明的 Trace 标记 API（Android 实现用 Systrace，iOS no-op） |
| `Platform.kt` | `expect` 声明的平台接口 |
| `ShiftPattern.kt` | 个人班次排期模型：基于锚定日期和周期长度的 WORK/OFF 状态计算 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `ui/` | 所有 UI 组件（见 `ui/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- `CalendarViewModel.kt` 是核心状态枢纽，包含 `selectedDate`、`isCollapsed`、`isYearView` 等 StateFlow
- `ShiftPattern` 使用模运算：`(date - anchorDate) mod cycle.size`
- 修改状态逻辑后务必运行 `commonTest` 中的测试

### Testing Requirements
- `ShiftPatternTest` — 班次计算验证
- `CalendarViewModelTest` — ViewModel 状态流验证
- `CalendarViewModelStateTest` — 状态转换验证

## Dependencies

### Internal
- `ui/` 目录 — UI 组件消费 ViewModel 状态

### External
- `kotlinx-datetime`, `kotlinx-coroutines`
- `tyme4kt`（农历日期转换）

<!-- MANUAL: -->
