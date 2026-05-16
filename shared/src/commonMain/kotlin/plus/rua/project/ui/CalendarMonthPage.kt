package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * 月度日历网格页面，支持两阶段折叠动画。
 *
 * Phase 1：所有行整体上移，直到选中行到达顶部 (y=0)，上方行被裁剪并淡出。
 * Phase 2：选中行固定不动，下方行整体上移并淡出。
 *
 * @param year 年份
 * @param month 月份（1-12）
 * @param selectedDate 当前选中日期
 * @param today 今天的日期，用于高亮标记
 * @param onDateClick 日期点击回调
 * @param collapseProgress 折叠进度，0f=展开，1f=折叠
 * @param rowHeightPx 从外层传入的锁定行高（像素），折叠过程中不变
 * @param effectiveWeeks 当前有效行数（含翻页插值），用于计算总高度
 * @param onRowHeightMeasured 首次行高测量回调，外层据此锁定行高
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
    rowHeightPx: Int,
    effectiveWeeks: Float,
    onRowHeightMeasured: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val days = remember(year, month) {
        generateMonthDays(year, month)
    }
    val density = LocalDensity.current

    val weeks = days.chunked(7)
    val anchorIndex = remember(weeks, selectedDate) {
        weeks.indexOfFirst { week -> week.any { it.date == selectedDate } }
    }
    val hasAnchor = anchorIndex >= 0
    val h = rowHeightPx.toFloat()

    // Phase 1 结束点：选中行到顶部所需的比例
    val phase1End = if (hasAnchor && anchorIndex > 0 && weeks.size > 1) {
        anchorIndex.toFloat() / (weeks.size - 1)
    } else 0f

    val phase1 = if (phase1End > 0f) {
        (collapseProgress / phase1End).coerceIn(0f, 1f)
    } else if (collapseProgress > 0f) 1f else 0f

    val phase2 = if (phase1End < 1f && collapseProgress > phase1End) {
        ((collapseProgress - phase1End) / (1f - phase1End)).coerceIn(0f, 1f)
    } else 0f

    val belowRowsHeight = if (hasAnchor) {
        (weeks.size - 1 - anchorIndex) * h
    } else 0f

    val totalHeightDp = if (rowHeightPx > 0) {
        val totalPx = h * (1 + (effectiveWeeks - 1) * (1f - collapseProgress))
        with(density) { totalPx.toDp() }
    } else null

    Box(
        modifier = modifier.clipToBounds().then(
            if (totalHeightDp != null) Modifier.height(totalHeightDp)
            else Modifier
        )
    ) {
        weeks.forEachIndexed { weekIndex, week ->
            val isAnchor = hasAnchor && weekIndex == anchorIndex
            val isAbove = hasAnchor && weekIndex < anchorIndex
            val isBelow = hasAnchor && weekIndex > anchorIndex

            val yOffsetDp = if (rowHeightPx > 0) {
                val yPx = when {
                    !hasAnchor -> weekIndex * h - collapseProgress * weeks.size * h
                    isAnchor -> anchorIndex * h * (1f - phase1)
                    isAbove -> weekIndex * h - phase1 * anchorIndex * h
                    isBelow -> weekIndex * h - phase1 * anchorIndex * h - phase2 * belowRowsHeight
                    else -> weekIndex * h
                }
                with(density) { yPx.toDp() }
            } else 0.dp

            val rowAlpha = when {
                !hasAnchor -> (1f - collapseProgress).coerceIn(0f, 1f)
                isAnchor -> 1f
                isAbove -> (1f - phase1).coerceIn(0f, 1f)
                isBelow -> (1f - phase2).coerceIn(0f, 1f)
                else -> 1f
            }

            if (rowAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isAnchor) 1f else 0f)
                        .then(
                            if (rowHeightPx > 0) Modifier.height(with(density) { h.toDp() })
                            else Modifier
                        )
                        .then(
                            if (isAnchor && collapseProgress > 0f) Modifier.background(MaterialTheme.colorScheme.surface)
                            else Modifier
                        )
                        .offset(y = yOffsetDp)
                        .then(
                            if (weekIndex == 0 && rowHeightPx == 0) {
                                Modifier.onSizeChanged { size ->
                                    if (size.height > 0) {
                                        onRowHeightMeasured?.invoke(size.height)
                                    }
                                }
                            } else Modifier
                        )
                        .padding(vertical = ROW_PADDING_DP.dp)
                        .then(
                            if (rowAlpha < 1f) Modifier.graphicsLayer { alpha = rowAlpha }
                            else Modifier
                        )
                ) {
                    week.forEach { dayData ->
                        DayCell(
                            date = dayData.date,
                            isCurrentMonth = dayData.isCurrentMonth,
                            isSelected = dayData.date == selectedDate,
                            isToday = dayData.date == today,
                            onClick = { onDateClick(dayData.date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

private data class DayData(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)

@Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
private fun generateMonthDays(year: Int, month: Int): List<DayData> {
    val firstOfMonth = LocalDate(year, month, 1)
    val offset = firstOfMonth.dayOfWeek.ordinal
    val startDate = firstOfMonth.minus(DatePeriod(days = offset))
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).day
    val rows = ((offset + daysInMonth - 1) / 7) + 1
    val totalDays = rows * 7

    return (0 until totalDays).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
        DayData(
            date = date,
            isCurrentMonth = date.month.number == month && date.year == year
        )
    }
}
