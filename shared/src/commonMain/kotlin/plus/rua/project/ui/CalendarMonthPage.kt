package plus.rua.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun CalendarMonthPage(
    year: Int,
    month: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    onDateClick: (LocalDate) -> Unit,
    collapseProgress: Float,
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

    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rowMeasured = rowHeightPx > 0
    val H = rowHeightPx.toFloat()

    // 总高度 = 6行 × 行高（展开时），或选中行高度（折叠时）
    val totalHeightDp = if (rowMeasured) {
        if (hasSelectedWeek) {
            // 选中行高度 + 上方行压缩高度 + 下方行压缩高度
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
    ).onSizeChanged { size ->
        if (collapseProgress > 0f) {
            println("[Page] totalH=${size.height}px p=$collapseProgress selWeek=$selectedWeekIndex rowH=$rowHeightPx")
        }
    }) {
        weeks.forEachIndexed { weekIndex, week ->
            val isSelected = hasSelectedWeek && weekIndex == selectedWeekIndex
            val isAbove = hasSelectedWeek && weekIndex < selectedWeekIndex
            val isBelow = hasSelectedWeek && weekIndex > selectedWeekIndex

            val rowScale = when {
                isAbove || isBelow -> 1f - collapseProgress
                else -> 1f
            }

            val rowHeightDp = if (rowMeasured && rowScale > 0.01f) {
                with(density) { (H * rowScale).toDp() }
            } else if (!rowMeasured) {
                null
            } else {
                0.dp
            }

            // 手动计算每行的视觉 y 位置
            val yOffsetDp = if (rowMeasured && hasSelectedWeek) {
                val yPx = when {
                    isAbove -> weekIndex * H * (1f - collapseProgress)
                    isSelected -> selectedWeekIndex * H * (1f - collapseProgress)
                    isBelow -> selectedWeekIndex * H * (1f - collapseProgress) + H + (weekIndex - selectedWeekIndex - 1) * H * (1f - collapseProgress)
                    else -> weekIndex * H
                }
                with(density) { yPx.toDp() }
            } else if (rowMeasured) {
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
                        .onSizeChanged { size ->
                            if (size.height > 0 && !rowMeasured) {
                                rowHeightPx = size.height
                            }
                        }
                        .padding(vertical = 4.dp)
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

@Suppress("DEPRECATION")
private fun generateMonthDays(year: Int, month: Int): List<DayData> {
    val firstOfMonth = LocalDate(year, month, 1)
    val offset = firstOfMonth.dayOfWeek.ordinal
    val startDate = firstOfMonth.minus(DatePeriod(days = offset))

    return (0 until 42).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
        DayData(
            date = date,
            isCurrentMonth = date.monthNumber == month && date.year == year
        )
    }
}