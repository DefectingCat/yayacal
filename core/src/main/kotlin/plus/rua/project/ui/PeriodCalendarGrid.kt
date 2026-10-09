package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.number
import plus.rua.project.PeriodDocument
import plus.rua.project.PeriodForecast
import kotlin.math.abs

/** 日格的经期标记，按优先级从高到低。 */
internal enum class PeriodDayMark { RECORDED, PREDICTED, OVULATION, FERTILE, NONE }

internal fun periodDayMark(
    date: LocalDate,
    today: LocalDate,
    document: PeriodDocument,
    forecast: PeriodForecast,
): PeriodDayMark = when {
    date <= today && document.rangeAt(date, today) != null -> PeriodDayMark.RECORDED
    forecast.isPredictedPeriod(date) -> PeriodDayMark.PREDICTED
    forecast.isOvulation(date) -> PeriodDayMark.OVULATION
    forecast.isFertile(date) -> PeriodDayMark.FERTILE
    else -> PeriodDayMark.NONE
}

/**
 * 经期月历卡片，左右滑动切换月份，底部附图例。
 *
 * @param document 包含未同步修改的经期文档
 * @param forecast 预测结果，提供预测经期、易孕期与排卵日
 * @param today 今天，用于展开进行中的经期并禁止编辑未来日期
 * @param isEditing 批量编辑模式；开启时未来日期不可点击
 * @param onDayClick 点击可点日期时触发；编辑模式下调用方应切换该日经期，否则打开单日面板
 * @param onToggleEditing 点击「编辑」或「完成」时触发
 * @param modifier 布局修饰符
 */
@Composable
internal fun PeriodCalendarGrid(
    document: PeriodDocument,
    forecast: PeriodForecast,
    today: LocalDate,
    isEditing: Boolean,
    onDayClick: (LocalDate) -> Unit,
    onToggleEditing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 页码以首次展示的月份为基准，跨天后 today 变化也不会让页码错位
    val anchor = remember { today }
    val initialYear = anchor.year
    val initialMonth = anchor.month.number
    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })
    val coroutineScope = rememberCoroutineScope()
    val viewYearMonth by remember {
        derivedStateOf { pageToYearMonth(pagerState.currentPage, initialYear, initialMonth) }
    }
    val density = LocalDensity.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                }
                Text(
                    text = "${viewYearMonth.first}年${viewYearMonth.second}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                }
                Spacer(Modifier.weight(1f))
                if (pagerState.currentPage != START_PAGE) {
                    TextButton(onClick = { coroutineScope.launch { pagerState.animateScrollToPage(START_PAGE) } }) {
                        Text("今天")
                    }
                }
                TextButton(onClick = onToggleEditing, modifier = Modifier.testTag("period_edit_toggle")) {
                    Text(if (isEditing) "完成" else "编辑")
                }
            }
            if (isEditing) {
                Text(
                    text = "点按日期标记或取消经期，未来日期不可编辑",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                )
            }

            Row(Modifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { title ->
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val rowHeightPx = constraints.maxWidth / 7
                // 滑动时在两页行数间插值，避免高度跳变
                val interpolatedWeeks by remember {
                    derivedStateOf {
                        val fraction = pagerState.currentPageOffsetFraction
                        val base = calculateWeeksCountForPage(pagerState.currentPage, anchor)
                        if (abs(fraction) > OFFSET_FRACTION_THRESHOLD) {
                            val target = calculateWeeksCountForPage(pagerState.currentPage + if (fraction > 0) 1 else -1, anchor)
                            lerp(base.toFloat(), target.toFloat(), abs(fraction))
                        } else {
                            base.toFloat()
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 0,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(with(density) { (rowHeightPx * interpolatedWeeks).toDp() })
                        .clipToBounds(),
                ) { page ->
                    val (year, month) = pageToYearMonth(page, initialYear, initialMonth)
                    PeriodMonthGrid(
                        year = year,
                        month = month,
                        document = document,
                        forecast = forecast,
                        today = today,
                        isEditing = isEditing,
                        onDayClick = onDayClick,
                    )
                }
            }

            PeriodLegend(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp))
        }
    }
}

@Composable
private fun PeriodMonthGrid(
    year: Int,
    month: Int,
    document: PeriodDocument,
    forecast: PeriodForecast,
    today: LocalDate,
    isEditing: Boolean,
    onDayClick: (LocalDate) -> Unit,
) {
    val gridInfo = remember(year, month) { getMonthGridInfo(year, month) }
    Column(Modifier.fillMaxWidth()) {
        repeat(gridInfo.rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayNum = row * 7 + col - gridInfo.offset + 1
                    if (dayNum in 1..gridInfo.daysInMonth) {
                        val date = LocalDate(year, Month(month), dayNum)
                        val enabled = !isEditing || date <= today
                        PeriodDayCell(
                            day = dayNum,
                            mark = periodDayMark(date, today, document, forecast),
                            isToday = date == today,
                            hasNote = document.noteAt(date) != null,
                            enabled = enabled,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f).testTag("period_day_$date"),
                        )
                    } else {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodDayCell(
    day: Int,
    mark: PeriodDayMark,
    isToday: Boolean,
    hasNote: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = periodPalette()
    val textColor =
        when (mark) {
            PeriodDayMark.RECORDED -> palette.onPeriod
            PeriodDayMark.PREDICTED -> palette.period
            PeriodDayMark.OVULATION, PeriodDayMark.FERTILE -> palette.onFertile
            PeriodDayMark.NONE -> MaterialTheme.colorScheme.onSurface
        }
    val todayRing = MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
        modifier
            .aspectRatio(1f)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(4.dp)
                .then(if (isToday) Modifier.border(1.5.dp, todayRing, CircleShape) else Modifier)
                .padding(if (isToday) 3.dp else 0.dp)
                .periodMark(mark, palette),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.toString(),
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isToday || mark == PeriodDayMark.RECORDED) FontWeight.Bold else FontWeight.Normal,
            )
        }
        if (hasNote) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 1.dp)
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            )
        }
    }
}

private fun Modifier.periodMark(
    mark: PeriodDayMark,
    palette: PeriodPalette,
): Modifier = when (mark) {
    PeriodDayMark.RECORDED -> background(palette.period, CircleShape)
    PeriodDayMark.PREDICTED -> dashedCircle(palette.period)
    PeriodDayMark.OVULATION -> background(palette.fertile, CircleShape).border(2.dp, palette.ovulation, CircleShape)
    PeriodDayMark.FERTILE -> background(palette.fertile, CircleShape)
    PeriodDayMark.NONE -> this
}

private fun Modifier.dashedCircle(color: Color): Modifier = drawBehind {
    val stroke = 1.5.dp.toPx()
    drawCircle(
        color = color,
        radius = size.minDimension / 2 - stroke / 2,
        center = Offset(size.width / 2, size.height / 2),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

@Composable
private fun PeriodLegend(modifier: Modifier = Modifier) {
    val palette = periodPalette()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem("经期", Modifier.background(palette.period, CircleShape))
        LegendItem("预测", Modifier.dashedCircle(palette.period))
        LegendItem("易孕期", Modifier.background(palette.fertile, CircleShape))
        LegendItem("排卵日", Modifier.background(palette.fertile, CircleShape).border(2.dp, palette.ovulation, CircleShape))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text("有记录", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendItem(
    label: String,
    swatch: Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).then(swatch))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
