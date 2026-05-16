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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * 月度日历网格页面，支持逐行向上滑出的折叠动画。
 *
 * 折叠时锚定行（包含选中日期）平滑移动到顶部固定，其余行从上到下依次向上滑出并淡出。
 * 下方行从锚定行背后经过（z-index 遮挡），所有行高度不变，仅做 y 平移。
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

    // Stagger 参数：每行的动画延迟和持续时间
    val totalNonAnchor = if (hasAnchor) weeks.size - 1 else weeks.size
    val staggerGap = if (totalNonAnchor > 1) 0.5f / totalNonAnchor else 0f
    val rowAnimDuration = if (totalNonAnchor > 1) {
        (1f - (totalNonAnchor - 1) * staggerGap).coerceAtLeast(0.1f)
    } else 1f

    val totalHeightDp = if (rowHeightPx > 0) {
        val totalPx = h * (1 + (effectiveWeeks - 1) * (1f - collapseProgress))
        with(density) { totalPx.toDp() }
    } else {
        null
    }

    Box(
        modifier = modifier.clipToBounds().then(
            if (totalHeightDp != null) Modifier.height(totalHeightDp)
            else Modifier
        )
    ) {
        weeks.forEachIndexed { weekIndex, week ->
            val isAnchor = hasAnchor && weekIndex == anchorIndex

            // 退出顺序：从上到下视觉顺序，锚定行跳过
            val exitOrder = when {
                !hasAnchor -> weekIndex
                weekIndex < anchorIndex -> weekIndex
                weekIndex == anchorIndex -> -1
                else -> weekIndex - 1
            }

            // 每行的局部进度（staggered）
            val localProgress = when {
                collapseProgress <= 0f -> 0f
                isAnchor -> collapseProgress
                exitOrder < 0 -> 0f
                totalNonAnchor <= 1 -> collapseProgress
                else -> ((collapseProgress - exitOrder * staggerGap) / rowAnimDuration).coerceIn(0f, 1f)
            }

            // Y 偏移
            val yOffsetDp = if (rowHeightPx > 0) {
                val yPx = if (isAnchor) {
                    anchorIndex * h * (1f - localProgress)
                } else {
                    val originalY = weekIndex * h
                    originalY - localProgress * (originalY + h)
                }
                with(density) { yPx.toDp() }
            } else 0.dp

            // 淡出
            val rowAlpha = if (isAnchor) 1f else (1f - localProgress).coerceIn(0f, 1f)

            if (rowAlpha > 0.01f) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isAnchor) 1f else 0f)
                        .then(
                            if (rowHeightPx > 0) Modifier.height(with(density) { h.toDp() })
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
