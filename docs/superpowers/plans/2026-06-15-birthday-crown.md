# 生日皇冠标识实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在月视图和周视图的日期单元格中，为阳历 9 月 4 日与农历正月二十一日显示左上角金色皇冠，并在点击时播放放大回弹动画。

**Architecture:** 复用 `:core` 已有的 `LunarCache` 计算每个日期的 `isBirthday` 标志，通过 `DayCellInfo` 传递到 `DayCell`，由 `DayCell` 在单元格左上角绘制旋转后的皇冠图标并处理点击动画。

**Tech Stack:** Android/Jetpack Compose, Material 3, tyme4kt, kotlinx-datetime, JUnit 4, Kotlin Coroutines Test

---

## 文件结构

| 文件 | 作用 |
|------|------|
| `core/src/main/res/drawable/ic_birthday_crown.xml` | 皇冠 Vector Drawable，保留 SVG 原始黄色/金色 |
| `core/src/main/kotlin/plus/rua/project/LunarCache.kt` | 新增 `isBirthday` 字段与生日判断逻辑 |
| `core/src/test/kotlin/plus/rua/project/LunarCacheBirthdayTest.kt` | 生日计算单元测试 |
| `core/src/main/kotlin/plus/rua/project/ui/DayCell.kt` | 在左上角显示皇冠并处理点击动画 |

---

### Task 1: 添加皇冠 Vector Drawable

**Files:**
- Create: `core/src/main/res/drawable/ic_birthday_crown.xml`

- [ ] **Step 1: 创建 Vector Drawable 文件**

```bash
mkdir -p core/src/main/res/drawable
```

```xml
<!-- core/src/main/res/drawable/ic_birthday_crown.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="512"
    android:viewportHeight="512">
    <path
        android:fillColor="#FFC033"
        android:pathData="M461.913,456.348H50.087c-9.223,0-16.696-7.473-16.696-16.696V372.87c0-9.223,7.473-16.696,16.696-16.696h411.826c9.223,0,16.696,7.473,16.696,16.696v66.783C478.609,448.875,471.136,456.348,461.913,456.348z" />
    <path
        android:fillColor="#FFE14D"
        android:pathData="M478.609,389.565H33.391V72.348c0-6.957,4.31-13.185,10.821-15.63c6.527-2.424,13.859-0.598,18.44,4.636l102.511,117.152l76.946-115.418c6.608-9.913,21.175-9.913,27.783,0l76.946,115.418L449.349,61.353c4.587-5.234,11.935-7.06,18.44-4.636c6.51,2.445,10.82,8.674,10.82,15.63V389.565z" />
    <path
        android:fillColor="#F37B2A"
        android:pathData="M256,322.783c-27.619,0-50.087-22.468-50.087-50.087s22.468-50.087,50.087-50.087s50.087,22.468,50.087,50.087S283.619,322.783,256,322.783z" />
    <path
        android:fillColor="#F37B2A"
        android:pathData="M50.087,322.783C22.468,322.783,0,300.315,0,272.696s22.468-50.087,50.087-50.087s50.087,22.468,50.087,50.087S77.706,322.783,50.087,322.783z" />
    <path
        android:fillColor="#F9A926"
        android:pathData="M461.913,356.174H256v100.174h205.913c9.223,0,16.696-7.473,16.696-16.696V372.87C478.609,363.647,471.136,356.174,461.913,356.174z" />
    <path
        android:fillColor="#FFCC33"
        android:pathData="M478.609,389.565V72.348c0-6.957-4.31-13.185-10.821-15.63c-6.506-2.424-13.853-0.598-18.44,4.636L346.837,178.505L269.891,63.087c-3.304-4.957-8.597-7.435-13.891-7.435v333.913H478.609z" />
    <path
        android:fillColor="#E56722"
        android:pathData="M306.087,272.696c0-27.619-22.468-50.087-50.087-50.087v100.174C283.619,322.783,306.087,300.315,306.087,272.696z" />
    <path
        android:fillColor="#E56722"
        android:pathData="M461.913,322.783c-27.619,0-50.087-22.468-50.087-50.087s22.468-50.087,50.087-50.087S512,245.077,512,272.696S489.532,322.783,461.913,322.783z" />
</vector>
```

- [ ] **Step 2: 验证资源可被引用**

构建一次确认资源文件格式正确：

```bash
./gradlew :core:mergeDebugResources
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/main/res/drawable/ic_birthday_crown.xml
git commit -m "feat: add birthday crown vector drawable"
```

---

### Task 2: 在 LunarCache 中计算生日并添加测试

**Files:**
- Modify: `core/src/main/kotlin/plus/rua/project/LunarCache.kt`
- Create: `core/src/test/kotlin/plus/rua/project/LunarCacheBirthdayTest.kt`

- [ ] **Step 1: 编写失败测试**

```kotlin
// core/src/test/kotlin/plus/rua/project/LunarCacheBirthdayTest.kt
package plus.rua.project

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCacheBirthdayTest {
    private val cache = LunarCache()

    @Test
    fun solarBirthdaySeptember4_returnsTrue() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 9, 4))
        assertTrue("阳历 9 月 4 日应为生日", info.isBirthday)
    }

    @Test
    fun lunarBirthdayFirstMonthDay21_returnsTrue() = runTest {
        // 2026 年农历正月二十一对应阳历 2026-03-09
        val info = cache.getOrCompute(LocalDate(2026, 3, 9))
        assertTrue("农历正月二十一应为生日", info.isBirthday)
    }

    @Test
    fun regularDate_returnsFalse() = runTest {
        val info = cache.getOrCompute(LocalDate(2026, 6, 15))
        assertFalse("普通日期不应为生日", info.isBirthday)
    }

    @Test
    fun solarBirthdayNotFirstLunarDay21_stillReturnsTrue() = runTest {
        // 2026 年 9 月 4 日是农历六月廿三，验证仅满足阳历条件时也返回 true
        val info = cache.getOrCompute(LocalDate(2026, 9, 4))
        assertTrue(info.isBirthday)
    }

    @Test
    fun lunarBirthdayNotSeptember4_stillReturnsTrue() = runTest {
        // 2026 年 3 月 9 日是阳历，不是 9 月 4 日，验证仅满足农历条件时也返回 true
        val info = cache.getOrCompute(LocalDate(2026, 3, 9))
        assertTrue(info.isBirthday)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```bash
./gradlew :core:testDebugUnitTest --tests "plus.rua.project.LunarCacheBirthdayTest"
```

Expected: 测试编译失败（`isBirthday` 不存在）或运行失败。

- [ ] **Step 3: 修改 LunarCache.kt**

在 `compute()` 中计算 `isBirthday`，并更新 `DayCellInfo` 数据类：

```kotlin
private fun compute(date: LocalDate): DayCellInfo {
    val solarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
    val holidayBadge = solarDay.getLegalHoliday()?.let { if (it.isWork()) "班" else "休" }
    val lunarDay = solarDay.getLunarDay()
    val lunarMonth = lunarDay.getLunarMonth()
    val lunarMonthName = lunarMonth.getName()

    val isBirthday = (date.month.number == 9 && date.day == 4) ||
        (lunarDay.getLunarMonth().getIndexInYear() == 0 && lunarDay.day == 21)

    // 农历传统节日（仅当天）
    val lunarFestival = lunarDay.getFestival()
    if (lunarFestival != null) {
        return DayCellInfo(lunarFestival.getName(), true, holidayBadge, lunarMonthName, isBirthday)
    }

    // 节气（当天才显示）
    val termDay = solarDay.getTermDay()
    if (termDay.getDayIndex() == 0) {
        return DayCellInfo(termDay.getSolarTerm().getName(), true, holidayBadge, lunarMonthName, isBirthday)
    }

    // 公历节日（仅当天）
    val solarFestival = solarDay.getFestival()
    if (solarFestival != null) {
        return DayCellInfo(solarFestival.getName(), true, holidayBadge, lunarMonthName, isBirthday)
    }

    // 默认：农历日期
    val name = lunarDay.getName()
    val text = if (name == "初一") {
        lunarMonthName
    } else {
        name
    }
    return DayCellInfo(text, false, holidayBadge, lunarMonthName, isBirthday)
}
```

更新 `DayCellInfo`：

```kotlin
data class DayCellInfo(
    val annotationText: String,
    val isAnnotationHighlight: Boolean,
    val holidayBadge: String?,
    val lunarMonthName: String? = null,
    val isBirthday: Boolean = false
)
```

- [ ] **Step 4: 运行测试确认通过**

```bash
./gradlew :core:testDebugUnitTest --tests "plus.rua.project.LunarCacheBirthdayTest"
```

Expected: `BUILD SUCCESSFUL` 且 5 个测试全部通过。

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/plus/rua/project/LunarCache.kt \
        core/src/test/kotlin/plus/rua/project/LunarCacheBirthdayTest.kt
git commit -m "feat: compute birthday flag in LunarCache"
```

---

### Task 3: 在 DayCell 中显示皇冠和点击动画

**Files:**
- Modify: `core/src/main/kotlin/plus/rua/project/ui/DayCell.kt`

- [ ] **Step 1: 添加所需 import**

在 `DayCell.kt` 顶部添加以下 import：

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import plus.rua.project.shared.R
```

- [ ] **Step 2: 修改 DayCellImpl 参数签名并添加动画状态**

在 `DayCellImpl` 函数体开始处（`val annotationText = lunarData.annotationText` 之前）添加：

```kotlin
    val isBirthday = lunarData.isBirthday
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

- [ ] **Step 3: 包装点击回调**

找到 `Box` 上的 `.clickable(...)` 调用，将 `onClick = onClick` 替换为：

```kotlin
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isBirthday) isPressedBirthday = true
                        onClick()
                    }
                ),
```

- [ ] **Step 4: 在单元格左上角添加皇冠**

在 `DayCellImpl` 最外层 `Box` 内部、班次标记 `if (shiftKind != null) { ... }` 之后添加：

```kotlin
        if (isBirthday) {
            Icon(
                painter = painterResource(R.drawable.ic_birthday_crown),
                contentDescription = "生日",
                tint = Color.Unspecified,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .padding(start = 2.dp, top = 2.dp)
                    .size(14.dp)
                    .graphicsLayer {
                        rotationZ = -45f
                        transformOrigin = TransformOrigin.Center
                        scaleX = crownScale.value
                        scaleY = crownScale.value
                    }
            )
        }
```

- [ ] **Step 5: 编译确认无错误**

```bash
./gradlew :core:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/plus/rua/project/ui/DayCell.kt
git commit -m "feat: show tilted birthday crown with click bounce animation"
```

---

### Task 4: 格式化与全量测试

**Files:**
- Modify: 前述所有文件（由 spotless 自动格式化）

- [ ] **Step 1: 运行代码格式化**

```bash
./gradlew spotlessApply
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 运行全量单元测试**

```bash
./gradlew :core:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` 且所有测试通过。

- [ ] **Step 3: 提交格式化改动**

```bash
git add -u
git commit -m "style: apply spotless formatting"
```

---

## Self-Review Checklist

1. **Spec coverage**
   - 阳历 9 月 4 日生日 → Task 2 `isBirthday` 计算 + Task 2 测试。
   - 农历正月二十一生日 → Task 2 `isBirthday` 计算 + Task 2 测试。
   - 仅月/周视图显示 → Task 3 仅修改 `DayCell`，不碰 `YearGridView`。
   - 左上角、左倾 45°、14dp、保留原色 → Task 3 皇冠代码。
   - 重合显示一个 → Task 2 使用 OR 逻辑，Task 3 单一 Icon。
   - 点击放大回弹动画 → Task 3 `Animatable` + `spring`。

2. **Placeholder scan**
   - 无 TBD/TODO；所有代码块完整；所有命令含预期输出。

3. **Type consistency**
   - `DayCellInfo.isBirthday` 在 Task 2 定义，Task 3 读取。
   - `R.drawable.ic_birthday_crown` 在 Task 1 创建，Task 3 引用。
   - tyme4kt API 使用 `LunarMonth.getIndexInYear()`（0-based，正月 = 0）与 `LunarDay.day`（Kotlin 属性），已通过本地 JVM 脚本验证。
