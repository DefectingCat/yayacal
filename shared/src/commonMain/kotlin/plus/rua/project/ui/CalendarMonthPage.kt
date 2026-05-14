package plus.rua.project.ui

import androidx.compose.foundation.layout.Column
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

    var rowHeightPx by remember { mutableIntStateOf(0) }
    val rowMeasured = rowHeightPx > 0

    // 选中行上移距离 = 上方行数 × 行高 × progress
    val selectedOffsetPx = if (rowMeasured) {
        -(selectedWeekIndex.toFloat() * rowHeightPx.toFloat() * collapseProgress)
    } else {
        0f
    }
    val selectedOffsetDp = with(density) { selectedOffsetPx.toDp() }

    Column(modifier = modifier.clipToBounds()) {
        weeks.forEachIndexed { weekIndex, week ->
            val isSelected = weekIndex == selectedWeekIndex
            val isAboveSelected = weekIndex < selectedWeekIndex
            val isBelowSelected = weekIndex > selectedWeekIndex

            // 非选中行高度跟手压缩
            val rowScale = when {
                isAboveSelected || isBelowSelected -> 1f - collapseProgress
                else -> 1f
            }

            val rowHeightDp = if (rowMeasured && rowScale > 0.01f) {
                with(density) { (rowHeightPx.toFloat() * rowScale).toDp() }
            } else if (!rowMeasured) {
                null
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
                        .then(
                            if (isSelected && rowMeasured) Modifier.offset(y = selectedOffsetDp)
                            else Modifier
                        )
                        .onSizeChanged { size ->
                            if (size.height > 0 && !rowMeasured) {
                                rowHeightPx = size.height
                            }
                        }
                        .padding(vertical = 2.dp)
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