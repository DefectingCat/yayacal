<!-- Parent: ../../../../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-06-16 -->

# ui

## Purpose
日历应用的所有 UI 组件层，基于 Jetpack Compose + Material 3 构建。包含月视图、周视图、年视图、底部卡片、日期单元格、顶部标题栏以及关于/许可证页面。

## Key Files

| File | Description |
|------|-------------|
| `CalendarMonthView.kt` | 月视图主屏幕：组合 MonthHeader + WeekdayHeader + CalendarPager + BottomCard，管理折叠/展开状态 |
| `CalendarMonthPage.kt` | 单月页面：6×7 日期网格，带选中日期高亮和非选中周压缩动画 |
| `CalendarPager.kt` | 月视图水平翻页器：`Int.MAX_VALUE` 页，映射到 yearMonth |
| `WeekPager.kt` | 周视图水平翻页器：折叠状态下替代 CalendarPager，单周分页 |
| `YearGridView.kt` | 年视图：4×3 迷你月网格，支持年份导航 |
| `MonthHeader.kt` | 月视图标题栏：显示年份月份 + ISO 周数 |
| `WeekdayHeader.kt` | 固定星期标题行：「一二三四五六日」 |
| `DayCell.kt` | 单个日期单元格：支持选中/今天/班次状态样式 |
| `BottomCard.kt` | 底部卡片：拖拽手柄，驱动折叠/展开手势，显示农历、节气、节假日和班次状态 |
| `AboutScreen.kt` | 关于页面：应用信息、开源许可入口 |
| `DogParkScreen.kt` | 小狗乐园彩蛋页面：全屏循环播放视频 |
| `LicensesScreen.kt` | 许可证列表页面 |
| `Licenses.kt` | 许可证数据（依赖项名称 + 许可证文本） |
| `AnimatedWebp.kt` | GIF 动画显示组件（基于 sketch） |
| `CalendarUtils.kt` | 日历工具：pager 常量、页码↔日期转换算法 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 新增 UI 组件保持 `Modifier` 参数最后
- 公共 `@Composable` 需要 KDoc（见 `COMMENTS.md`）
- 所有 UI 文本使用中文
- `CalendarMonthView` 是顶层屏幕组件，修改前理解折叠动画和 pager 交互

### Testing Requirements
- `CalendarUtilsTest` — 页码与日期转换验证
- `CalendarUtilsExtraTest` — 边界情况测试

### Common Patterns
- Pager 使用 `Int.MAX_VALUE` 页，中心点为 `Int.MAX_VALUE / 2`
- 折叠动画通过 `collapseProgress`（0f=月视图, 1f=周视图）驱动
- `WeekPager` 仅在完全折叠时替换 `CalendarPager`
- 日历网格为 6 行 × 7 列（ISO 周，周一为起始）

## Dependencies

### Internal
- `core/src/main/kotlin/plus/rua/project/CalendarViewModel.kt` — 状态源
- `core/src/main/kotlin/plus/rua/project/ShiftPattern.kt` — 班次数据

### External
- Jetpack Compose, Material 3
- `kotlinx-datetime`
- `tyme4kt`（农历、节气、节日）
- `sketch`（GIF）

<!-- MANUAL: -->
