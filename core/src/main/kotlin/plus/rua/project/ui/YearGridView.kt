package plus.rua.project.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyme.lunar.LunarYear
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import plus.rua.project.composeTraceBeginSection
import plus.rua.project.composeTraceEndSection
import kotlin.math.roundToInt
import plus.rua.project.composeTraceBeginSection
import plus.rua.project.composeTraceEndSection

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

private data class MiniMonthColors(
    val titleSelected: Color,
    val titleNormal: Color,
    val weekday: Color,
    val day: Color,
    val otherMonth: Color,
    val todayBg: Color,
    val todayText: Color
)

/**
 * 年视图 4×3 月历网格。
 *
 * @param year 显示的年份
 * @param selectedMonth 当前选中月份（1-12）
 * @param today 今天的日期
 * @param onMonthClick 月份点击回调
 * @param modifier 外部布局修饰符
 */
@Composable
fun YearGridView(
    year: Int,
    selectedMonth: Int,
    today: LocalDate,
    onMonthClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    monthModifier: @Composable (Int) -> Modifier = { Modifier }
) {
    composeTraceBeginSection("YearGridView:$year")

    // P0-F: 主题色在 YearGridView 级别一次性读取并缓存
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(colorScheme) {
        MiniMonthColors(
            titleSelected = colorScheme.primary,
            titleNormal = colorScheme.onSurface,
            weekday = colorScheme.onSurface.copy(alpha = 0.4f),
            day = colorScheme.onSurface.copy(alpha = 0.6f),
            otherMonth = colorScheme.onSurface.copy(alpha = 0.2f),
            todayBg = colorScheme.primaryContainer,
            todayText = colorScheme.onPrimaryContainer
        )
    }

    // P0-F: 预计算全年 12 个月的日期数据，翻年时复用
    val monthDays = remember(year) {
        (1..12).map { generateMiniMonthDays(year, it) }
    }

    // P0-G: 共享 TextMeasurer
    val textMeasurer = rememberTextMeasurer()
    val dayTextStyle = remember { TextStyle(fontSize = 8.sp, lineHeight = 12.sp) }

    // P0: 使用 remember 同步预测量所有可能的字符样式，消除 produceState 首帧空白/闪烁
    val dayLayouts = remember(textMeasurer, dayTextStyle, colors) {
        val days = 1..31
        val colorTypes = listOf(0 to colors.day, 1 to colors.todayText, 2 to colors.otherMonth)
        val map = HashMap<Int, androidx.compose.ui.text.TextLayoutResult>(32 * 3)
        days.forEach { d ->
            colorTypes.forEach { (type, c) ->
                map[d * 3 + type] = textMeasurer.measure(d.toString(), dayTextStyle.copy(color = c))
            }
        }
        map
    }

    val titleLayouts = remember(textMeasurer, colors) {
        val map = HashMap<Int, androidx.compose.ui.text.TextLayoutResult>(13 * 2)
        (1..12).forEach { month ->
            val text = "${month}月"
            map[month * 2 + 1] = textMeasurer.measure(
                text,
                TextStyle(fontSize = 10.sp, color = colors.titleSelected, fontWeight = FontWeight.Bold)
            )
            map[month * 2] = textMeasurer.measure(
                text,
                TextStyle(fontSize = 10.sp, color = colors.titleNormal)
            )
        }
        map
    }

    val weekdayLayouts = remember(textMeasurer, colors) {
        WEEKDAY_LABELS.associateWith { label ->
            textMeasurer.measure(label, TextStyle(fontSize = 8.sp, color = colors.weekday))
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val gridRows = if (isLandscape) 3 else 4
    val gridCols = if (isLandscape) 4 else 3

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("year_grid"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 4×3 月历网格
        // 弹性平分纵向空间，横竖屏自适应
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 4.dp, end = 4.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            (0 until gridRows).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0 until gridCols).forEach { col ->
                        val month = row * gridCols + col + 1
                        if (month <= 12) {
                            MiniMonth(
                                year = year,
                                month = month,
                                isSelected = month == selectedMonth,
                                today = today,
                                days = monthDays[month - 1],
                                colors = colors,
                                dayLayouts = dayLayouts,
                                titleLayouts = titleLayouts,
                                weekdayLayouts = weekdayLayouts,
                                onClick = { onMonthClick(month) },
                                modifier = Modifier.weight(1f).then(monthModifier(month))
                            )
                        }
                    }
                }
            }
        }
    }
    composeTraceEndSection()
}

/**
 * 精简版月历：月份标题 + 星期行 + 日期数字网格，全部 Canvas 绘制。
 *
 * 消除 Text 组件避免 TextStringSimpleNode::measure 开销。
 */
@Composable
private fun MiniMonth(
    year: Int,
    month: Int,
    isSelected: Boolean,
    today: LocalDate,
    days: List<MiniDayData>,
    colors: MiniMonthColors,
    dayLayouts: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    titleLayouts: Map<Int, androidx.compose.ui.text.TextLayoutResult>,
    weekdayLayouts: Map<String, androidx.compose.ui.text.TextLayoutResult>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dayRowCount = days.size / 7
    val titleHeightPx = with(density) { 14.sp.toPx() }
    val titleToWeekdayGapPx = with(density) { 4.dp.toPx() }
    val weekdayHeightPx = with(density) { 12.sp.toPx() }
    val dayCellHeightPx = with(density) { (12.sp.toPx() + 4.dp.toPx()) }
    val totalHeight = with(density) {
        (titleHeightPx + titleToWeekdayGapPx + weekdayHeightPx + dayRowCount * dayCellHeightPx).toDp()
    }

    var isPressed by remember { mutableStateOf(false) }
    val pressedScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "mini_month_press_scale"
    )

    val containsToday = remember(days, today) {
        days.any { it.isCurrentMonth && it.date == today }
    }
    val semanticsDesc = remember(year, month, containsToday) {
        "$year 年 $month 月" + if (containsToday) "，包含今天" else ""
    }

    val containerShape = RoundedCornerShape(12.dp)
    val selectedBgColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
    val selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    val containerModifier = if (isSelected) {
        Modifier
            .background(color = selectedBgColor, shape = containerShape)
            .border(width = 1.dp, color = selectedBorderColor, shape = containerShape)
    } else Modifier

    Column(
        modifier = modifier
            .then(containerModifier)
            .graphicsLayer {
                scaleX = pressedScale
                scaleY = pressedScale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .semantics {
                contentDescription = semanticsDesc
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            val cellWidth = size.width / 7f

            // 1. 绘制标题（像素对齐避免抗锯齿模糊）
            val titleLayout = titleLayouts[month * 2 + (if (isSelected) 1 else 0)]
            if (titleLayout != null) {
                val titleX = ((size.width - titleLayout.size.width) / 2f).roundToInt().toFloat()
                drawText(
                    textLayoutResult = titleLayout,
                    topLeft = Offset(titleX, 0f)
                )
            }

            // 2. 绘制星期行
            val weekdayY = titleHeightPx + titleToWeekdayGapPx
            WEEKDAY_LABELS.forEachIndexed { i, label ->
                weekdayLayouts[label]?.let { layout ->
                    val x = (i * cellWidth + (cellWidth - layout.size.width) / 2f).roundToInt().toFloat()
                    val y = (weekdayY + (weekdayHeightPx - layout.size.height) / 2f).roundToInt().toFloat()
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(x, y)
                    )
                }
            }

            // 3. 绘制日期网格
            val dayGridY = weekdayY + weekdayHeightPx

            days.forEachIndexed { index, dayData ->
                val row = index / 7
                val col = index % 7
                val centerX = (col * cellWidth + cellWidth / 2f).roundToInt().toFloat()
                val centerY = (dayGridY + row * dayCellHeightPx + dayCellHeightPx / 2f).roundToInt().toFloat()

                val isToday = dayData.date == today && dayData.isCurrentMonth
                val dayNum = if (dayData.isCurrentMonth) dayData.date.day else 0
                val colorType = when {
                    !dayData.isCurrentMonth -> 2
                    isToday -> 1
                    else -> 0
                }

                if (isToday) {
                    val radius = (cellWidth.coerceAtMost(dayCellHeightPx) / 2f * 0.8f).roundToInt().toFloat()
                    drawCircle(
                        color = colors.todayBg,
                        radius = radius,
                        center = Offset(centerX, centerY)
                    )
                }

                if (dayNum > 0) {
                    dayLayouts[dayNum * 3 + colorType]?.let { layoutResult ->
                        drawText(
                            textLayoutResult = layoutResult,
                            topLeft = Offset(
                                x = (centerX - layoutResult.size.width / 2f).roundToInt().toFloat(),
                                y = (centerY - layoutResult.size.height / 2f).roundToInt().toFloat()
                            )
                        )
                    }
                }
            }
        }
    }
}

private data class MiniDayData(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)

private fun generateMiniMonthDays(year: Int, month: Int): List<MiniDayData> {
    composeTraceBeginSection("generateMiniMonthDays:$year-$month")
    val info = getMonthGridInfo(year, month)
    val result = (0 until info.totalDays).map { i ->
        val date = info.startDate.plus(DatePeriod(days = i))
        MiniDayData(
            date = date,
            isCurrentMonth = date.month.number == month && date.year == year
        )
    }
    composeTraceEndSection()
    return result
}

/**
 * 年视图标题栏，左侧显示年份文字与农历干支年，右侧在非今年时显示「今年」按钮。
 *
 * 年份切换时年份与农历年文字均有垂直滑动过渡动画。
 *
 * @param year 当前年份
 * @param currentYear 今年年份
 * @param onYearChange 年份切换回调
 * @param modifier 外部布局修饰符
 */
@Composable
fun YearHeader(
    year: Int,
    currentYear: Int,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedContent(
                targetState = year,
                label = "year_header_title",
                transitionSpec = {
                    val isForward = targetState > initialState
                    val slideIn = slideInVertically(tween(260)) { height -> if (isForward) -height else height } + fadeIn(tween(180))
                    val slideOut = slideOutVertically(tween(260)) { height -> if (isForward) height else -height } + fadeOut(tween(180))
                    slideIn togetherWith slideOut
                }
            ) { y ->
                Text(
                    text = "${y}年",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            val showThisYear = year != currentYear
            val thisYearAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showThisYear) 1f else 0f,
                animationSpec = tween(200),
                label = "this_year_alpha"
            )
            val thisYearScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (showThisYear) 1f else 0.8f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "this_year_scale"
            )
            Surface(
                onClick = { if (showThisYear) onYearChange(currentYear) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.graphicsLayer {
                    alpha = thisYearAlpha
                    scaleX = thisYearScale
                    scaleY = thisYearScale
                }
            ) {
                Text(
                    text = "今年",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        AnimatedContent(
            targetState = year,
            label = "year_header_lunar",
            transitionSpec = {
                val isForward = targetState > initialState
                val slideIn = slideInVertically(tween(260)) { height -> if (isForward) -height else height } + fadeIn(tween(180))
                val slideOut = slideOutVertically(tween(260)) { height -> if (isForward) height else -height } + fadeOut(tween(180))
                slideIn togetherWith slideOut
            },
            modifier = Modifier.padding(top = 4.dp)
        ) { y ->
            Text(
                text = lunarYearLabel(y),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * 返回类似「丙午马年」的农历干支生肖年标签。
 */
private fun lunarYearLabel(year: Int): String {
    val sixtyCycle = LunarYear.fromYear(year).getSixtyCycle()
    val ganZhi = sixtyCycle.getName()
    val zodiac = sixtyCycle.getEarthBranch().getZodiac().getName()
    return "${ganZhi}${zodiac}年"
}
