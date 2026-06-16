# 玫瑰日图标设计

## 背景

在日历单元格上为每年 **10 月 16 日** 增加一个小玫瑰图标，作为纪念日标识。实现方式参考已有的生日皇冠图标配置。

## 目标

- 每年 10 月 16 日的单元格左上角显示玫瑰图标。
- 与生日皇冠共用同一套渲染位置、尺寸和点击动画。
- 若 10 月 16 日同时命中生日，优先显示玫瑰图标。
- 不引入额外的无障碍文本，图标仅作为视觉装饰。

## 方案选择

采用**方案一：沿用生日模式做最小扩展**。理由：

- 与现有生日代码完全同构，改动最小；
- 符合用户“参考已有生日的配置”的要求；
- 可读性最好，降低维护者理解成本。

## 详细设计

### 数据层

文件：`core/src/main/kotlin/plus/rua/project/LunarCache.kt`

在 `DayCellInfo` 中新增字段：

```kotlin
data class DayCellInfo(
    val annotationText: String,
    val isAnnotationHighlight: Boolean,
    val holidayBadge: String?,
    val lunarMonthName: String? = null,
    val isBirthday: Boolean = false,
    val isRoseDay: Boolean = false,
)
```

在 `LunarCache.compute()` 中加入玫瑰日判断：

```kotlin
val isRoseDay = date.month.number == 10 && date.day == 16
```

并将 `isRoseDay` 传入所有 `DayCellInfo(...)` 构造分支。

### UI 层

文件：`core/src/main/kotlin/plus/rua/project/ui/DayCell.kt`

在 `DayCellImpl` 中：

1. 读取 `val isRoseDay = lunarData.isRoseDay`。
2. 新增 `roseClickTick` 与 `roseScale` 状态。
3. 在 `clickable` 的 `onClick` 中：若 `isRoseDay` 则触发 `roseClickTick += 1`，随后调用外部 `onClick()`。
4. 在 `Box` 的左上角优先渲染玫瑰图标：
   - 若 `isRoseDay` 为 `true`，显示 `R.drawable.ic_rose`；
   - 否则若 `isBirthday` 为 `true`，显示 `R.drawable.ic_birthday_crown`。
5. 图标尺寸、内边距与皇冠保持一致：
   - `size(14.dp)`
   - `padding(start = 2.dp, top = 2.dp)`
   - `rotationZ = -45f`
   - 点击时通过 `graphicsLayer` 的 `scaleX / scaleY` 播放弹跳动画。

### 资源

- 源文件：`~/Pictures/rose-svgrepo-com.svg`
- 目标路径：`core/src/main/res/drawable/ic_rose.xml`
- 转换方式：将 SVG 转为 Android VectorDrawable，保留原始填充色，渲染时使用 `Color.Unspecified`。

### 测试

新增文件：`core/src/test/kotlin/plus/rua/project/LunarCacheRoseDayTest.kt`

覆盖以下场景：

1. `LocalDate(2026, 10, 16)` 的 `isRoseDay` 为 `true`。
2. 普通日期（如 `LocalDate(2026, 6, 15)`）的 `isRoseDay` 为 `false`。
3. 非 10 月的 16 日（如 `LocalDate(2026, 9, 16)`）的 `isRoseDay` 为 `false`。

## 改动文件

- `core/src/main/kotlin/plus/rua/project/LunarCache.kt`
- `core/src/main/kotlin/plus/rua/project/ui/DayCell.kt`
- `core/src/main/res/drawable/ic_rose.xml`（新增）
- `core/src/test/kotlin/plus/rua/project/LunarCacheRoseDayTest.kt`（新增）

## 风险与注意事项

- 图标与生日皇冠同位置，需确保 `isRoseDay` 判断在前，避免被生日皇冠覆盖。
- SVG 转换后需检查 `viewportWidth` / `viewportHeight` 与 `pathData` 是否正确。
- 新增字段后 `DayCellInfo` 的 `copy` / 解构等用法不受影响。
