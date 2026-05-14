package plus.rua.project.ui

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * 月度日历网格页面，支持折叠动画。
 *
 * 折叠时非选中行高度按 (1-p) 缩放，选中行保持原始高度，
 * 所有行通过手动 y-offset 定位，形成向选中行收缩的视觉效果。
 *
 * @param rowHeightPx 从外层传入的锁定行高（像素），折叠过程中不变
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
    onRowHeightMeasured: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val days = remember(year, month) {
        generateMonthDays(year, month)
    }
    val density = LocalDensity.current

    val weeks = days.chunked(7)
    val selectedWeekIndex = remember(weeks, selectedDate) {
        weeks.indexOfFirst { week -> week.any { it.date == selectedDate } }
    }

    val hasSelectedWeek = selectedWeekIndex >= 0
    val H = rowHeightPx.toFloat()

    // 总高度 = 选中行高度 + 上方行压缩高度 + 下方行压缩高度
    val totalHeightDp = if (rowHeightPx > 0) {
        if (hasSelectedWeek) {
            val aboveH = selectedWeekIndex * H * (1f - collapseProgress)
            val belowH = (weeks.size - 1 - selectedWeekIndex) * H * (1f - collapseProgress)
            val selH = H
            with(density) { (aboveH + selH + belowH).toDp() }
        } else {
            with(density) { (weeks.size * H).toDp() }
        }
    } else {
        null
    }

    Box(modifier = modifier.clipToBounds().then(
        if (totalHeightDp != null) Modifier.height(totalHeightDp)
        else Modifier
    )) {
        weeks.forEachIndexed { weekIndex, week ->
            val isSelected = hasSelectedWeek && weekIndex == selectedWeekIndex
            val isAbove = hasSelectedWeek && weekIndex < selectedWeekIndex
            val isBelow = hasSelectedWeek && weekIndex > selectedWeekIndex

            val rowScale = when {
                isAbove || isBelow -> 1f - collapseProgress
                else -> 1f
            }

            val rowHeightDp = if (rowHeightPx > 0 && rowScale > 0.01f) {
                with(density) { (H * rowScale).toDp() }
            } else if (rowHeightPx <= 0) {
                null
            } else {
                0.dp
            }

            val yOffsetDp = if (rowHeightPx > 0 && hasSelectedWeek) {
                val yPx = when {
                    isAbove -> weekIndex * H * (1f - collapseProgress)
                    isSelected -> selectedWeekIndex * H * (1f - collapseProgress)
                    isBelow -> selectedWeekIndex * H * (1f - collapseProgress) + H + (weekIndex - selectedWeekIndex - 1) * H * (1f - collapseProgress)
                    else -> weekIndex * H
                }
                with(density) { yPx.toDp() }
            } else if (rowHeightPx > 0) {
                with(density) { (weekIndex * H).toDp() }
            } else {
                0.dp
            }

            val shouldShow = rowHeightDp == null || rowHeightDp > 0.dp

            if (shouldShow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isSelected) 1f else 0f)
                        .then(
                            if (rowHeightDp != null) Modifier.height(rowHeightDp)
                            else Modifier
                        )
                        .offset(y = yOffsetDp)
                        .padding(vertical = 4.dp)
                        .then(
                            if (weekIndex == 0 && rowHeightPx == 0) {
                                Modifier.onSizeChanged { size ->
                                    if (size.height > 0) onRowHeightMeasured?.invoke(size.height)
                                }
                            } else Modifier
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
    val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).dayOfMonth
    val rows = ((offset + daysInMonth - 1) / 7) + 1
    val totalDays = rows * 7

    return (0 until totalDays).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
        DayData(
            date = date,
            isCurrentMonth = date.monthNumber == month && date.year == year
        )
    }
}