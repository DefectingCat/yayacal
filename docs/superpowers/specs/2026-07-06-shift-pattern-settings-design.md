# 个人轮班设置页设计

## 背景

当前 `ShiftPattern` 在 `CalendarViewModel` 中硬编码（锚点 `2026-05-15`，周期 `[WORK, WORK, OFF, OFF]`），用户无法修改，重启后回到默认值（注释明确标注"MVP 默认，后续接入设置页与持久化"）。

本设计新增一个独立设置页，让用户能：
- 编辑基础周期（锚点日期 + 班次序列）；
- 对单日做调班（翻转某天班/休）；
- 设置相位断点（从某天起重排周期相位）；
- 持久化到 SharedPreferences，设置返回后主界面立即生效。

## 目标

- 用户能从主界面 FAB 菜单进入"班次设置"页。
- 基础周期通过预设方案选择（1班1休 / 2班2休 / 3班3休 / 4班4休）。
- 锚点日期通过 DatePicker 修改。
- 迷你月历上：**点某天 = 翻转班/休**（单日 override），**长按某天 = 设/清相位断点**。
- 设置页改动通过 `LaunchedEffect` 自动存盘，返回主界面后 `onResume` 触发 VM 重读，立即刷新日历。
- 单元测试覆盖核心算法（`kindAt` 含 overrides/phaseBreaks）、持久化往返、VM 重读。

## 需求语义（用户原始例子）

基础周期 `[WORK, WORK, OFF, OFF]` 锚点 7/8。期望 7/10–7/21 序列：

| 日期 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 | 18 | 19 | 20 | 21 |
|------|----|----|----|----|----|----|----|----|----|----|----|----|
| 目标 | 休 | 休 | **休** | 班 | **班** | **班** | **休** | **休** | **班** | **班** | **休** | **休** |

实现方式：
- 7/10、7/11、7/13：基础周期自动算出，无需调班。
- 7/12（休）、7/14（班）、7/15（班）、7/16（休）、7/17（休）：单日 override 翻转。
- 7/18 起：相位断点重排（`PhaseBreak(date=7/18, cycleOffset=0)`），18 当作 `cycle[0]` 重新循环，自动得到 18–21 = 班班休休。

**结论**：单靠 override 或单靠 phaseBreak 都无法表达此例，必须**两者结合**。

## 方案选择

采用**独立 Activity + SharedPreferences + onResume 重读**。理由：

- 与项目现有架构一致（`DateCheckerActivity` + `DateCheckerStorage` 是现成的"设置页 + 持久化"完整范例，无 DI、无 Navigation、无 DataStore）。
- 改动隔离，不污染主界面 Composable。
- `onResume` 重读实现简单、体验顺滑（设置返回立即生效），无需引入 Flow 持久化或 ContentObserver。

备选方案（未采用）：
- 底部弹窗 Dialog：与多 Activity 架构不一致，弹窗内 UI 拥挤。
- 全屏覆盖层：增加主 Composable 复杂度，违背"改动隔离"原则。
- 实时同步（Flow + OnSharedPreferenceChangeListener）：过度设计，设置页返回一次重读已足够。

## 详细设计

### 数据层

文件：`core/src/main/kotlin/plus/rua/project/ShiftPattern.kt`

新增 `PhaseBreak` 数据类，并扩展 `ShiftPattern`：

```kotlin
data class PhaseBreak(
    val date: LocalDate,      // 从这天起重排
    val cycleOffset: Int      // 这天对应 cycle 的第几位（0 = cycle[0]）
)

data class ShiftPattern(
    val anchorDate: LocalDate,
    val cycle: List<ShiftKind>,
    val overrides: Map<LocalDate, ShiftKind> = emptyMap(),   // 单日翻转
    val phaseBreaks: List<PhaseBreak> = emptyList(),         // 相位重排
    val name: String = "默认"
) {
    fun kindAt(date: LocalDate): ShiftKind? {
        if (cycle.isEmpty()) return null
        overrides[date]?.let { return it }                   // 1. override 优先
        val (anchor, offset) = activeAnchor(date)             // 2. 找活跃锚点
        val diff = anchor.daysUntil(date)
        val size = cycle.size
        val idx = (((diff + offset) % size) + size) % size
        return cycle[idx]
    }

    private fun activeAnchor(date: LocalDate): Pair<LocalDate, Int> {
        val applicable = phaseBreaks.filter { it.date <= date }.maxByOrNull { it.date }
        return if (applicable != null) applicable.date to applicable.cycleOffset
        else anchorDate to 0
    }
}
```

`activeAnchor` 语义：在 `date` 当天或之前（`<=`）的 phaseBreaks 中取日期最大的那个；若无则回退到基础 `anchorDate`（offset=0）。`kindAt` 先查 override，未命中再按活跃锚点推算。

> 注：`cycleOffset` 保留为可配置字段（当前 UI 仅写入 0），为未来"断点从周期中段开始"留扩展点，YAGNI 之下不暴露到 UI。

### 持久化

新文件：`core/src/main/kotlin/plus/rua/project/ShiftPatternStorage.kt`

照抄 `DateCheckerStorage` 模式（SharedPreferences + `fromContext` 工厂 + save/load/clear），不引入新依赖。

```kotlin
class ShiftPatternStorage(private val prefs: SharedPreferences) {
    companion object {
        private const val KEY_ANCHOR = "shift_anchor"        // "2026-07-08"（ISO）
        private const val KEY_CYCLE = "shift_cycle"          // "1,1,0,0"（1=WORK 0=OFF）
        private const val KEY_OVERRIDES = "shift_overrides"  // "2026-07-12:0,2026-07-14:1"
        private const val KEY_BREAKS = "shift_breaks"        // "2026-07-18:0"
        private const val SEPARATOR_PAIR = ","

        fun fromContext(context: Context): ShiftPatternStorage =
            ShiftPatternStorage(
                context.getSharedPreferences("shift_pattern", Context.MODE_PRIVATE)
            )
    }

    fun save(pattern: ShiftPattern)
    fun load(): ShiftPattern?
    fun clear()
}
```

编码格式（无 JSON 依赖，与 `DateCheckerStorage` 风格一致）：
- 锚点：`LocalDate.toString()` → `"2026-07-08"`。
- 周期：`"1,1,0,0"`（逗号分隔；`1=WORK 0=OFF`）。
- overrides/breaks：`"日期:值,日期:值"`（逗号分隔的 key:value 对；overrides 的值 0/1，breaks 的值是 cycleOffset）。

`load()` 在锚点或周期缺失/解析失败时返回 `null`，由 VM 回退到 `DEFAULT_PATTERN`。

### ViewModel 接入

文件：`core/src/main/kotlin/plus/rua/project/CalendarViewModel.kt`

1. 构造参数新增 `shiftStorage: ShiftPatternStorage? = null`（可空，便于测试注入与保持默认构造可用）。
2. `_shiftPattern` 初始值改为 `loadShiftPattern()`。
3. 新增 `refreshShiftPattern()`：从 storage 重读后写入 `_shiftPattern.value`。
4. 新增 `companion object DEFAULT_PATTERN`（迁移原硬编码值：锚点 `2026-05-15`，周期 `[WORK,WORK,OFF,OFF]`）。

```kotlin
class CalendarViewModel(
    private val clock: Clock = Clock.System,
    private val shiftStorage: ShiftPatternStorage? = null
) : ViewModel() {

    private val _shiftPattern = MutableStateFlow(loadShiftPattern())
    val shiftPattern: StateFlow<ShiftPattern?> = _shiftPattern.asStateFlow()

    private fun loadShiftPattern(): ShiftPattern =
        shiftStorage?.load() ?: DEFAULT_PATTERN   // storage 为空或未存 → 用默认(非空)

    fun refreshShiftPattern() {
        _shiftPattern.value = loadShiftPattern()
    }

    companion object {
        val DEFAULT_PATTERN = ShiftPattern(
            anchorDate = LocalDate(2026, 5, 15),
            cycle = listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF)
        )
    }
}
```

`shiftKindAt(date)` 逻辑不变（内部 `shiftPattern.value?.kindAt(date)` 已自动支持 overrides/phaseBreaks）。

### VM 创建方式（Factory）

文件：`core/src/main/kotlin/plus/rua/project/ui/CalendarMonthView.kt`

`viewModel<CalendarViewModel>()` 改为带 factory 的写法：

```kotlin
val context = LocalContext.current.applicationContext
val viewModel: CalendarViewModel = viewModel(
    factory = viewModelFactory {
        initializer {
            CalendarViewModel(
                clock = Clock.System,
                shiftStorage = ShiftPatternStorage.fromContext(context)
            )
        }
    }
)
```

`androidx.lifecycle.viewmodel.viewModelFactory` / `initializer` 已随 lifecycle-viewmodel-compose（2.11.0）提供，无需加依赖。

### onResume 重读（立即生效）

文件：`core/src/main/kotlin/plus/rua/project/ui/CalendarMonthView.kt`

在 Composable 中监听生命周期，设置页返回时刷新：

```kotlin
val lifecycleOwner = LocalLifecycleOwner.current
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshShiftPattern()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

设置页改完 → 用户返回 → 主 Activity `onResume` → VM 重读 storage → `shiftPattern` flow 发新值 → 日历自动重组刷新。

### 设置页 UI

新文件：`core/src/main/kotlin/plus/rua/project/ui/ShiftPatternScreen.kt`

照抄 `DateCheckerScreen` 的 storage 创建 + 状态管理 + 自动存盘模式：

```kotlin
val context = LocalContext.current.applicationContext
val storage = remember { ShiftPatternStorage.fromContext(context) }
val saved = remember { storage.load() }
var pattern by remember { mutableStateOf(saved ?: CalendarViewModel.DEFAULT_PATTERN) }
LaunchedEffect(pattern) { storage.save(pattern) }
```

页面结构（三段式）：

**Section 1 — 基础周期**
- 锚点日期 Card：点击弹 `DatePickerDialog`（复用 `DateCheckerScreen` 的 `rememberDatePickerState` + `toLocalDate` 工具）。
- 预设周期 `FlowRow`：`FilterChip` 列表（1班1休 / 2班2休 / 3班3休 / 4班4休），当前选中高亮。点击 → `pattern.copy(cycle = preset, overrides = emptyMap(), phaseBreaks = emptyList())`（换周期清空调班，避免错位）。

**Section 2 — 迷你月历（核心交互）**

新文件：`core/src/main/kotlin/plus/rua/project/ui/ShiftCalendarGrid.kt`

- 月份切换 `<` / `>` 头部。
- 每个日期格 `Box.aspectRatio(1f).combinedClickable(onClick = ::toggleOverride, onLongClick = ::togglePhaseBreak)`。
- 右上角角标显示当天状态：
  - 断点当天 → 琥珀色 `tertiary` + "断"标。
  - 班 → `primary` + "班"标。
  - 休 → `error` + "休"标。
- 月历下方提示文案："点 = 翻转班/休，长按 = 设/清断点"。
- 图例：颜色含义说明。

交互逻辑：
- `toggleOverride(date)`：算出该天不含 override 时的基础值 `base`；当前值翻转得到 `newVal`；`newVal == base` 则从 overrides 移除该 date，否则加入 `date to newVal`。
- `togglePhaseBreak(date)`：已有则移除，否则加入 `PhaseBreak(date, 0)`。
- `combinedClickable` 已在 Compose Foundation 提供，无需新依赖。

**Section 3 — 恢复默认**
- 底部 `OutlinedButton("恢复默认")` → `AlertDialog` 确认（复用 `DateCheckerScreen` 的对话框样式）→ 重置为 `CalendarViewModel.DEFAULT_PATTERN`。

### 入口：FAB 菜单项

文件：`core/src/main/kotlin/plus/rua/project/ui/CalendarMonthView.kt`

在"显示调休"菜单项之后新增：
```kotlin
MenuItem(text = "班次设置", selected = false, onClick = {
    isMenuExpanded = false
    onNavigateToShiftSettings()
})
```
`CalendarMonthView` 签名新增 `onNavigateToShiftSettings: () -> Unit = {}`。

### Activity 与导航

新文件：`app/src/main/kotlin/plus/rua/project/ShiftPatternActivity.kt`（照抄 `DateCheckerActivity`，20 行薄壳）：
```kotlin
class ShiftPatternActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YaYaTheme {
                ShiftPatternScreen(onBack = { finishWithSlideBack() })
            }
        }
    }
}
```

文件改动：
- `app/src/main/kotlin/plus/rua/project/MainActivity.kt`：`CalendarMonthView(...)` 调用处加 `onNavigateToShiftSettings = { startActivityWithSlide(Intent(this, ShiftPatternActivity::class.java)) }`。
- `app/src/main/AndroidManifest.xml`：注册 `<activity android:name=".ShiftPatternActivity" android:exported="false" />`。

### 颜色方案（与主日历 DayCell 一致）

| 元素 | 颜色 |
|------|------|
| 班 | `MaterialTheme.colorScheme.primary` |
| 休 | `MaterialTheme.colorScheme.error` |
| 断点标记 | `MaterialTheme.colorScheme.tertiary` |

## 测试

| 测试文件 | 覆盖内容 |
|---------|---------|
| `core/src/test/.../ShiftPatternTest.kt`（改） | `kindAt` 含 overrides / phaseBreaks / 两者结合；用户原始例子全序列断言 |
| `core/src/test/.../ShiftPatternStorageTest.kt`（新） | save→load 往返、空值返回 null、格式解析、复用 `InMemorySharedPreferences` |
| `core/src/test/.../CalendarViewModelTest.kt`（改） | `refreshShiftPattern()` 从 mock storage 重读；storage 为 null 时回退 `DEFAULT_PATTERN` |

## 改动文件

- `core/src/main/kotlin/plus/rua/project/ShiftPattern.kt`（改：加 `PhaseBreak`、`overrides`、`phaseBreaks`、新 `kindAt`）
- `core/src/main/kotlin/plus/rua/project/ShiftPatternStorage.kt`（新）
- `core/src/main/kotlin/plus/rua/project/ui/ShiftPatternScreen.kt`（新）
- `core/src/main/kotlin/plus/rua/project/ui/ShiftCalendarGrid.kt`（新）
- `core/src/main/kotlin/plus/rua/project/CalendarViewModel.kt`（改：注入 storage、`refreshShiftPattern`、`DEFAULT_PATTERN`）
- `core/src/main/kotlin/plus/rua/project/ui/CalendarMonthView.kt`（改：VM factory、onResume 重读、菜单项、新回调参数）
- `app/src/main/kotlin/plus/rua/project/ShiftPatternActivity.kt`（新）
- `app/src/main/kotlin/plus/rua/project/MainActivity.kt`（改：接导航回调）
- `app/src/main/AndroidManifest.xml`（改：注册 Activity）
- `core/src/test/kotlin/plus/rua/project/ShiftPatternTest.kt`（改）
- `core/src/test/kotlin/plus/rua/project/ShiftPatternStorageTest.kt`（新）
- `core/src/test/kotlin/plus/rua/project/CalendarViewModelTest.kt`（改）

## 风险与注意事项

- **换预设周期时必须清空 overrides 和 phaseBreaks**，否则旧调班数据与新周期错位。`copy(cycle = preset, overrides = emptyMap(), phaseBreaks = emptyList())` 已处理。
- **VM Factory 改动**：原 `viewModel<CalendarViewModel>()` 默认构造改为 factory 注入，需确认现有测试（`CalendarViewModelTest` 通过 `FixedClock` 直接构造 VM）不受影响——构造参数 `shiftStorage` 可空且有默认值，直接构造仍可用。
- **迷你月历性能**：单月 42 格，`kindAt` 是 O(phaseBreaks) 查找，phaseBreaks 数量预期极小（个位数），无需优化。
- **combinedClickable 长按冲突**：需确认与系统长按菜单无冲突；`combinedClickable` 默认不触发文本选择，无额外风险。
- **存储格式向后兼容**：未来若改格式，`load()` 应在解析失败时返回 null 回退默认值，避免崩溃。
