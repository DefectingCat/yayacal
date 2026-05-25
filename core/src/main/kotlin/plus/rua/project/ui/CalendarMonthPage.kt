package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
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
import plus.rua.project.LunarCache
import plus.rua.project.ShiftKind


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
 * @param shiftKindAt 日期 → 个人轮班类型的查询闭包
 * @param showLegalHoliday 是否显示法定调休背景色。详见 [DayCell] 的同名参数。
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
    shiftKindAt: (LocalDate) -> ShiftKind?,
    showLegalHoliday: Boolean,
    onRowHeightMeasured: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val days = remember(year, month) {
        generateMonthDays(year, month)
    }
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    val holidayBadges by produceState(
        initialValue = emptyMap<LocalDate, String?>(),
        key1 = year,
        key2 = month
    ) {
        val map = mutableMapOf<LocalDate, String?>()
        for (dayData in days) {
            val info = LunarCache.default.getOrCompute(dayData.date)
            map[dayData.date] = info.holidayBadge
        }
        value = map
    }

    val holidayEdges = remember(holidayBadges, year, month) {
        val map = mutableMapOf<LocalDate, HolidayEdgeInfo>()
        for (dayData in days) {
            val date = dayData.date
            val badge = holidayBadges[date]
            if (badge == null) continue
            val prevBadge = holidayBadges[date.minus(DatePeriod(days = 1))]
            val nextBadge = holidayBadges[date.plus(DatePeriod(days = 1))]
            map[date] = HolidayEdgeInfo(
                isStart = prevBadge != badge,
                isEnd = nextBadge != badge
            )
        }
        map
    }

    val weeks = remember(days) { days.chunked(7) }
    val anchorIndex = remember(year, month, selectedDate) {
        weeks.indexOfFirst { week -> week.any { it.date == selectedDate } }
    }
    val totalHeightDp = if (rowHeightPx > 0) {
        val h = rowHeightPx.toFloat()
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
            key(weekIndex) {
                WeekRow(
                    weekIndex = weekIndex,
                    week = week,
                    anchorIndex = anchorIndex,
                    weeksSize = weeks.size,
                    collapseProgress = collapseProgress,
                    rowHeightPx = rowHeightPx,
                    selectedDate = selectedDate,
                    today = today,
                    shiftKindAt = shiftKindAt,
                    showLegalHoliday = showLegalHoliday,
                    holidayEdges = holidayEdges,
                    onDateClick = onDateClick,
                    onRowHeightMeasured = onRowHeightMeasured,
                    interactionSource = interactionSource
                )
            }
        }
    }
}

@Composable
private fun WeekRow(
    weekIndex: Int,
    week: List<DayData>,
    anchorIndex: Int,
    weeksSize: Int,
    collapseProgress: Float,
    rowHeightPx: Int,
    selectedDate: LocalDate,
    today: LocalDate,
    shiftKindAt: (LocalDate) -> ShiftKind?,
    showLegalHoliday: Boolean,
    holidayEdges: Map<LocalDate, HolidayEdgeInfo>,
    onDateClick: (LocalDate) -> Unit,
    onRowHeightMeasured: ((Int) -> Unit)?,
    interactionSource: MutableInteractionSource,
) {
    val density = LocalDensity.current
    val hasAnchor = anchorIndex >= 0
    val h = rowHeightPx.toFloat()
    val isAnchor = hasAnchor && weekIndex == anchorIndex
    val isAbove = hasAnchor && weekIndex < anchorIndex
    val isBelow = hasAnchor && weekIndex > anchorIndex

    val phase1End = if (hasAnchor && anchorIndex > 0 && weeksSize > 1) {
        anchorIndex.toFloat() / (weeksSize - 1)
    } else 0f

    val phase1 = if (phase1End > 0f) {
        (collapseProgress / phase1End).coerceIn(0f, 1f)
    } else if (collapseProgress > 0f) 1f else 0f

    val phase2 = if (phase1End < 1f && collapseProgress > phase1End) {
        ((collapseProgress - phase1End) / (1f - phase1End)).coerceIn(0f, 1f)
    } else 0f

    val belowRowsHeight = if (hasAnchor) {
        (weeksSize - 1 - anchorIndex) * h
    } else 0f

    val yOffsetPx = if (rowHeightPx > 0) {
        when {
            !hasAnchor -> weekIndex * h - collapseProgress * weeksSize * h
            isAnchor -> anchorIndex * h * (1f - phase1)
            isAbove -> weekIndex * h - phase1 * anchorIndex * h
            isBelow -> weekIndex * h - phase1 * anchorIndex * h - phase2 * belowRowsHeight
            else -> weekIndex * h
        }
    } else 0f

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
                    if (isAnchor && phase1 >= 1f) Modifier.background(MaterialTheme.colorScheme.surface)
                    else Modifier
                )
                .graphicsLayer {
                    translationY = yOffsetPx
                    this.alpha = rowAlpha
                }
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
        ) {
            week.forEach { dayData ->
                key(dayData.date) {
                    DayCell(
                        date = dayData.date,
                        isCurrentMonth = dayData.isCurrentMonth,
                        isSelected = dayData.date == selectedDate,
                        isToday = dayData.date == today,
                        shiftKind = shiftKindAt(dayData.date),
                        showLegalHoliday = showLegalHoliday,
                        holidayEdgeInfo = holidayEdges[dayData.date],
                        onClick = { onDateClick(dayData.date) },
                        modifier = Modifier.weight(1f),
                        interactionSource = interactionSource
                    )
                }
            }
        }
    }
}

private data class DayData(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)

/**
 * 法定假日在连续序列中的边缘状态，用于决定背景圆角。
 *
 * @param isStart 是否为同类型连续假日的开始（前一天不是同类型）
 * @param isEnd 是否为同类型连续假日的结束（后一天不是同类型）
 */
data class HolidayEdgeInfo(
    val isStart: Boolean,
    val isEnd: Boolean
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
