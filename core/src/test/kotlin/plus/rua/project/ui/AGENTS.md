<!-- Parent: ../../../../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# ui

## Purpose
UI 工具函数测试，覆盖日历工具类 `CalendarUtils` 的日期转换算法。

## Key Files

| File | Description |
|------|-------------|
| `CalendarUtilsTest.kt` | `CalendarUtils` 核心函数测试：页码↔年月、周起始计算 |
| `CalendarUtilsExtraTest.kt` | `CalendarUtils` 边界情况和额外场景测试 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 修改 `CalendarUtils.kt` 中的转换算法后应更新测试
- 测试覆盖 `pageToYearMonth`、`yearMonthToPage`、`pageToWeekMonday` 等函数

## Dependencies

### Internal
- `shared/src/commonMain/kotlin/plus/rua/project/ui/CalendarUtils.kt`

<!-- MANUAL: -->
