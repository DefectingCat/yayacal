<!-- Parent: ../../../../../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# project

## Purpose
核心逻辑层测试，覆盖 ViewModel 状态管理和班次模型计算。

## Key Files

| File | Description |
|------|-------------|
| `ShiftPatternTest.kt` | `ShiftPattern` 班次状态计算测试 |
| `CalendarViewModelTest.kt` | `CalendarViewModel` 状态流和行为测试 |
| `CalendarViewModelStateTest.kt` | ViewModel 状态转换边界测试 |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `ui/` | UI 工具函数测试（见 `ui/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 修改 `CalendarViewModel` 或 `ShiftPattern` 后应更新对应测试
- 测试使用 molecule 进行状态流断言

## Dependencies

### Internal
- `shared/src/commonMain/kotlin/plus/rua/project/ShiftPattern.kt`
- `shared/src/commonMain/kotlin/plus/rua/project/CalendarViewModel.kt`

<!-- MANUAL: -->
