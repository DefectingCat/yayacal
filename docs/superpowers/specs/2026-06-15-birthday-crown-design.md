# 生日皇冠标识设计文档

## 1. 功能概述

在月视图（`CalendarMonthPage`）和周视图（`WeekPager`）的日期单元格中，当日期满足以下任一条件时，在单元格左上角显示一个向左倾斜 45° 的金色皇冠图标：

1. **阳历生日**：每年 9 月 4 日
2. **农历生日**：每年正月二十一日

若两个条件在同一天满足，只显示一个皇冠。

用户点击生日当天单元格时，皇冠执行一个先放大后缩回原尺寸的响应动画。

## 2. 设计决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| 日期规则 | 写死两个日期 | 用户需求明确，改动最小 |
| 显示范围 | 仅月视图和周视图 | 年视图使用 `Canvas` 统一绘制，改动成本高 |
| 图标位置 | 单元格左上角 | 右上角已有班次标记，左上角空闲 |
| 图标方向 | 向左倾斜 45°（`rotationZ = -45f`） | 用户明确要求 |
| 图标大小 | 14 dp | 在 12-16 dp 范围内，不遮挡日期数字 |
| 图标颜色 | 保留 SVG 原色（黄色/金色） | 用户明确要求 |
| 重合处理 | 只显示一个皇冠 | 用户需求 |
| 动画幅度 | 放大到 1.4 倍后回弹 | 明显但不过度，使用 `spring` 实现 |

## 3. 数据模型改动

### 3.1 `DayCellInfo` 新增字段

**文件**：`core/src/main/kotlin/plus/rua/project/LunarCache.kt`

```kotlin
data class DayCellInfo(
    val annotationText: String,
    val isAnnotationHighlight: Boolean,
    val holidayBadge: String?,
    val lunarMonthName: String? = null,
    val isBirthday: Boolean = false
)
```

### 3.2 生日判断逻辑

在 `LunarCache.compute()` 中，利用已有的 `solarDay` 与 `lunarDay` 对象计算：

```kotlin
val isBirthday = (date.month.number == 9 && date.day == 4) ||
    (lunarDay.getLunarMonth().getIndexInYear() == 0 && lunarDay.day == 21)
```

> 注意：`tyme4kt` 中 `LunarMonth.getIndexInYear()` 返回 0-based 索引（正月 = 0），`LunarDay.day` 是 Kotlin 属性，返回农历日。

## 4. 资源处理

**来源文件**：`~/Downloads/crown-svgrepo-com.svg`

**目标路径**：`core/src/main/res/drawable/ic_birthday_crown.xml`

使用 Android Studio 的 Vector Asset Studio 将 SVG 转换为 Android Vector Drawable XML。在 `DayCell` 中通过 `painterResource` 加载，并使用 `Icon` 的 `tint = Color.Unspecified` 以保留 SVG 原始黄色/金色。

## 5. UI 改动

### 5.1 皇冠显示

**文件**：`core/src/main/kotlin/plus/rua/project/ui/DayCell.kt`

在 `DayCellImpl` 的 `Box` 内部新增左上角皇冠：

```kotlin
if (dayCellInfo.isBirthday) {
    Icon(
        painter = painterResource(R.drawable.ic_birthday_crown),
        contentDescription = "生日",
        tint = Color.Unspecified,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 2.dp, top = 2.dp)
            .size(14.dp)
            .graphicsLayer {
                rotationZ = -45f
                scaleX = crownScale.value
                scaleY = crownScale.value
            }
    )
}
```

### 5.2 点击动画

在 `DayCellImpl` 中维护局部状态 `isPressedBirthday` 和一个 `Animatable`：

```kotlin
var isPressedBirthday by remember { mutableStateOf(false) }
val crownScale = remember { Animatable(1f) }
LaunchedEffect(isPressedBirthday) {
    if (isPressedBirthday) {
        crownScale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        crownScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        isPressedBirthday = false
    }
}
```

在 `onClick` 中触发：

```kotlin
onClick = {
    if (dayCellInfo.isBirthday) isPressedBirthday = true
    onClick()
}
```

皇冠显示时使用 `crownScale.value`：

```kotlin
.graphicsLayer {
    rotationZ = -45f
    scaleX = crownScale.value
    scaleY = crownScale.value
}
```

## 6. 测试计划

新增文件：`core/src/test/kotlin/plus/rua/project/LunarCacheBirthdayTest.kt`

覆盖以下场景：

- 阳历 9 月 4 日返回 `isBirthday = true`
- 农历正月二十一日返回 `isBirthday = true`
- 非生日日期返回 `false`
- 两个生日重合的日期仍返回 `true`（UI 层保证只显示一个皇冠）

## 7. 涉及文件清单

| 文件 | 改动 |
|------|------|
| `core/src/main/kotlin/plus/rua/project/LunarCache.kt` | 新增 `isBirthday` 字段与计算逻辑 |
| `core/src/main/kotlin/plus/rua/project/ui/DayCell.kt` | 新增皇冠图标、旋转、点击动画 |
| `core/src/main/res/drawable/ic_birthday_crown.xml` | 新增皇冠 Vector Drawable |
| `core/src/test/kotlin/plus/rua/project/LunarCacheBirthdayTest.kt` | 新增生日计算单元测试 |

## 8. 非目标

- 不在年视图（`YearGridView`）中显示皇冠。
- 不提供用户界面添加、编辑或删除生日。
- 不将生日信息持久化到 `SharedPreferences` 或数据库。
