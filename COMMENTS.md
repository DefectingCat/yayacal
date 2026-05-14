# YaYa 注释规范

基于 [KDoc](https://kotlinlang.org/docs/kotlin-doc.html) 规范与 Compose 语义化约定，结合项目实际情况制定。

## 核心原则

1. **注释解释「为什么」，而非「做什么」**——代码本身应能说明做什么，注释补充意图、约束和决策原因
2. **宁可没有注释，也不要废话注释**——当前项目风格是自描述代码 + 极少注释，这是好的基线
3. **公共 API 必须有 KDoc，内部实现按需注释**

## 何时必须写注释

### 1. Public Composable 函数——KDoc 必需

所有 `public` 的 `@Composable` 函数必须写 KDoc，说明用途、参数含义和回调触发时机。

```kotlin
/**
 * 月度日历视图，支持周/月切换和折叠动画。
 *
 * @param selectedDate 当前选中日期
 * @param today 今天的日期，用于高亮标记
 * @param onDateClick 日期点击回调
 * @param collapseProgress 折叠进度，0f=展开，1f=折叠
 * @param modifier 外部布局修饰符
 */
@Composable
fun CalendarMonthPage(
    year: Int,
    month: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    collapseProgress: Float,
    modifier: Modifier = Modifier
)
```

**约定：**
- `Modifier` 参数放最后，KDoc 中注明"外部布局修饰符"
- 回调参数用 `on` 前缀命名（`onDateClick` 而非 `dateClick`），KDoc 说明触发时机
- `Float` 进度/比例参数注明取值范围和含义

### 2. 非显而易见的算法和计算

```kotlin
// 6行×7列=42格，覆盖跨月首尾周，保证网格完整
return (0 until 42).map { i -> ... }

// 折叠时选中行上方行上移、下方行下移，模拟"挤压"效果
val offsetY = when {
    isAboveSelected -> -progress * 200f
    isBelowSelected -> progress * 200f
    else -> 0f
}
```

### 3. Magic Number

用命名常量 + 注释，而非裸数字：

```kotlin
// 好
val MaxPagerPages = Int.MAX_VALUE  // 无限分页，中心页为起始月
val CenterPage = MaxPagerPages / 2

// 坏
HorizontalPager(pageCount = 2147483647) { ... }
```

### 4. Workaround / Hack / 平台限制

必须注明原因和追踪信息：

```kotlin
@Suppress("DEPRECATION")  // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
currentMonth = weekMonday.monthNumber
```

### 5. 状态与副作用的业务意图

对 `remember`、`LaunchedEffect`、`DisposableEffect` 等解释业务意图，而非技术实现：

```kotlin
// 仅在首次展开时记录完整日历高度，折叠后不再覆盖
if (!viewModel.isCollapsed && viewModel.collapseProgress < 0.01f) {
    expandedCalendarHeightPx = size.height
}
```

### 6. 动画参数与业务状态的映射

```kotlin
// collapseProgress: 0f=月视图(6行), 1f=周视图(1行)
// 折叠偏移量 = 进度 × 展开高度的5/6（保留1行可见）
val collapseOffsetPx = -(viewModel.collapseProgress * expandedCalendarHeightPx * 5f / 6f).toInt()
```

## 何时不需要注释

### 1. 代码本身已足够清晰

```kotlin
// 坏——废话注释
// 设置水平 padding 为 16dp
modifier = Modifier.padding(horizontal = 16.dp)

// 好——无需注释
modifier = Modifier.padding(horizontal = 16.dp)
```

### 2. 函数名已说明用途

```kotlin
// 坏
// 获取 ISO 周号
fun getIsoWeekNumber(date: LocalDate): Int

// 好——函数名已足够
fun getIsoWeekNumber(date: LocalDate): Int
```

### 3. 简单的属性赋值和数据类

```kotlin
// 不需要注释
data class DayData(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)
```

## KDoc 格式规范

### Composable 函数模板

```kotlin
/**
 * 一句话描述组件用途。
 *
 * 可选：补充说明重组行为、副作用等。
 *
 * @param param1 参数说明
 * @param param2 参数说明
 * @param modifier 外部布局修饰符
 */
@Composable
fun MyComponent(
    param1: Type,
    param2: Type,
    modifier: Modifier = Modifier
) { ... }
```

### ViewModel / 工具函数模板

```kotlin
/**
 * 一句话描述功能。
 *
 * @param input 输入说明
 * @return 返回值说明
 */
fun calculateSomething(input: Int): Int { ... }
```

### 文件级注释

仅在文件包含多个紧密关联的组件且关系不直观时使用，大多数文件不需要：

```kotlin
/**
 * 日历分页组件，包含月视图和周视图的 HorizontalPager 实现。
 */
```

## 项目特定约定

| 场景 | 规范 |
|------|------|
| `@Suppress("DEPRECATION")` | 必须附带行内注释说明原因（当前主要用于 `monthNumber`） |
| Pager 页码映射 | `pageToYearMonth()` / `yearMonthToPage()` 等算术映射需注释公式逻辑 |
| 折叠动画参数 | 注释 `collapseProgress` 的取值范围和物理含义 |
| 尺寸测量（px） | 注释为何需要 px 而非 dp（如：动画偏移量需要像素精度） |
| `remember` key | 当 key 列表不直观时，注释为何选择这些 key |
| `Int.MAX_VALUE` 分页 | 注释无限分页的设计意图和中心页计算方式 |

## Preview 注释

Preview 函数不需要 KDoc，但 `@Preview` 注解应提供有意义的 `name`：

```kotlin
@Preview(name = "Month View - Expanded", showBackground = true)
@Preview(name = "Month View - Collapsed", showBackground = true)
@Composable
private fun CalendarMonthViewPreview() { ... }
```

## 反模式清单

```kotlin
// ❌ 翻译代码
// 遍历每一周
weeks.forEachIndexed { ... }

// ❌ 过时注释
// TODO: 后续优化（已完成但未删除）

// ❌ 注释掉的代码
// val oldImplementation = ...

// ❌ 用 // 替代 KDoc
// 这个函数渲染日历
@Composable fun Calendar() { ... }

// ❌ 无原因的 Suppress
@Suppress("DEPRECATION")
fun foo() { ... }
```

## 检查清单

- [ ] Public Composable 是否有 KDoc？
- [ ] KDoc 是否说明了参数含义和回调触发时机？
- [ ] `Modifier` 参数是否在最后？
- [ ] 非显而易见的计算是否有行内注释？
- [ ] Magic Number 是否有命名常量 + 注释？
- [ ] `@Suppress` 是否有原因注释？
- [ ] 动画/进度参数是否注明了取值范围？
- [ ] 是否存在废话注释、过时注释或注释掉的代码？
