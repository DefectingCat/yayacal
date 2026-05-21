package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyme.lunar.LunarYear
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
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
 * @param sharedTransitionScope 共享元素转场作用域
 * @param animatedVisibilityScope 动画可见性作用域
 * @param modifier 外部布局修饰符
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun YearGridView(
    year: Int,
    selectedMonth: Int,
    today: LocalDate,
    onMonthClick: (Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
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

    // P0-D: 预测量 1..31 × 3 种颜色 = 93 个 TextLayoutResult
    val dayLayouts = remember(textMeasurer, dayTextStyle, colors) {
        val days = 1..31
        val colorList = listOf(colors.day, colors.todayText, colors.otherMonth)
        days.flatMap { d ->
            colorList.map { c ->
                (d to c) to textMeasurer.measure(d.toString(), dayTextStyle.copy(color = c))
            }
        }.toMap()
    }

    // P0-H: 预测量月份标题（选中/非选中两种颜色）
    val titleLayouts = remember(textMeasurer, colors) {
        (1..12).flatMap { month ->
            val text = "${month}月"
            listOf(
                (month to true) to textMeasurer.measure(
                    text,
                    TextStyle(
                        fontSize = 10.sp,
                        color = colors.titleSelected,
                        fontWeight = FontWeight.Bold
                    )
                ),
                (month to false) to textMeasurer.measure(
                    text,
                    TextStyle(fontSize = 10.sp, color = colors.titleNormal)
                )
            )
        }.toMap()
    }

    // P0-H: 预测量星期标签
    val weekdayLayouts = remember(textMeasurer, colors) {
        WEEKDAY_LABELS.associateWith { label ->
            textMeasurer.measure(label, TextStyle(fontSize = 8.sp, color = colors.weekday))
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 4×3 月历网格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            (0 until 4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0 until 3).forEach { col ->
                        val month = row * 3 + col + 1
                        val sharedKey = "month_grid_${year}_$month"
                        with(sharedTransitionScope) {
                            MiniMonth(
                                month = month,
                                isSelected = month == selectedMonth,
                                today = today,
                                days = monthDays[month - 1],
                                colors = colors,
                                dayLayouts = dayLayouts,
                                titleLayouts = titleLayouts,
                                weekdayLayouts = weekdayLayouts,
                                onClick = { onMonthClick(month) },
                                modifier = Modifier
                                    .weight(1f)
                                    .sharedElement(
                                        sharedContentState = rememberSharedContentState(
                                            key = sharedKey
                                        ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        boundsTransform = { _, _ ->
                                            tween(400, easing = FastOutSlowInEasing)
                                        }
                                    )
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
    month: Int,
    isSelected: Boolean,
    today: LocalDate,
    days: List<MiniDayData>,
    colors: MiniMonthColors,
    dayLayouts: Map<Pair<Int, Color>, androidx.compose.ui.text.TextLayoutResult>,
    titleLayouts: Map<Pair<Int, Boolean>, androidx.compose.ui.text.TextLayoutResult>,
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

    Column(
        modifier = modifier
            .padding(2.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            val cellWidth = size.width / 7f

            // 1. 绘制标题
            val titleLayout = titleLayouts[month to isSelected]!!
            drawText(
                textLayoutResult = titleLayout,
                topLeft = Offset(
                    (size.width - titleLayout.size.width) / 2f,
                    0f
                )
            )

            // 2. 绘制星期行
            val weekdayY = titleHeightPx + titleToWeekdayGapPx
            WEEKDAY_LABELS.forEachIndexed { i, label ->
                val layout = weekdayLayouts[label]!!
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        i * cellWidth + (cellWidth - layout.size.width) / 2f,
                        weekdayY + (weekdayHeightPx - layout.size.height) / 2f
                    )
                )
            }

            // 3. 绘制日期网格
            val dayGridY = weekdayY + weekdayHeightPx

            days.forEachIndexed { index, dayData ->
                val row = index / 7
                val col = index % 7
                val centerX = col * cellWidth + cellWidth / 2f
                val centerY = dayGridY + row * dayCellHeightPx + dayCellHeightPx / 2f

                val isToday = dayData.date == today && dayData.isCurrentMonth
                val dayNum = if (dayData.isCurrentMonth) dayData.date.day else 0
                val textColor: Color = when {
                    !dayData.isCurrentMonth -> colors.otherMonth
                    isToday -> colors.todayText
                    else -> colors.day
                }

                if (isToday) {
                    val radius = cellWidth.coerceAtMost(dayCellHeightPx) / 2f * 0.8f
                    drawCircle(
                        color = colors.todayBg,
                        radius = radius,
                        center = Offset(centerX, centerY)
                    )
                }

                if (dayNum > 0) {
                    dayLayouts[dayNum to textColor]?.let { layoutResult ->
                        drawText(
                            textLayoutResult = layoutResult,
                            topLeft = Offset(
                                x = centerX - layoutResult.size.width / 2f,
                                y = centerY - layoutResult.size.height / 2f
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

@Suppress("DEPRECATION") // monthNumber 无替代 API
private fun generateMiniMonthDays(year: Int, month: Int): List<MiniDayData> {
    composeTraceBeginSection("generateMiniMonthDays:$year-$month")
    val firstOfMonth = LocalDate(year, month, 1)
    val offset = firstOfMonth.dayOfWeek.ordinal
    val startDate = firstOfMonth.minus(DatePeriod(days = offset))
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).day
    val rows = ((offset + daysInMonth - 1) / 7) + 1
    val totalDays = rows * 7

    val result = (0 until totalDays).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
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
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically(tween(250)) { -it } togetherWith
                                slideOutVertically(tween(250)) { it }
                    } else {
                        slideInVertically(tween(250)) { it } togetherWith
                                slideOutVertically(tween(250)) { -it }
                    }
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
            val thisYearAlpha by animateFloatAsState(
                targetValue = if (showThisYear) 1f else 0f,
                animationSpec = tween(200)
            )
            Text(
                text = "今年",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .graphicsLayer { alpha = thisYearAlpha }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = showThisYear) { onYearChange(currentYear) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        AnimatedContent(
            targetState = year,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically(tween(250)) { -it } togetherWith
                            slideOutVertically(tween(250)) { it }
                } else {
                    slideInVertically(tween(250)) { it } togetherWith
                            slideOutVertically(tween(250)) { -it }
                }
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
