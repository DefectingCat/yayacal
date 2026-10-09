package plus.rua.project.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.datetime.number
import plus.rua.project.CycleLength
import plus.rua.project.PeriodForecast
import plus.rua.project.PeriodRegularity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** 深色模式下趋势线用稍暗的玫红，保证细线在深色卡片上的明度落在可读区间。 */
private val DarkTrendColor = Color(0xFFE85A80)

/** 纵轴刻度：按数据范围取 5 或 10 天的整齐间隔，上下各留一点余量。 */
internal fun cycleAxisTicks(values: List<Int>): List<Int> {
    val low = values.min() - 2
    val high = values.max() + 2
    val step = if (high - low <= 20) 5 else 10
    val start = floor(low / step.toDouble()).toInt() * step
    val end = ceil(high / step.toDouble()).toInt() * step
    return (start..end step step).toList()
}

/**
 * 历史页顶部的周期长度趋势卡片：最近 12 个完整周期的折线，点击数据点查看开始日期与天数。
 *
 * @param cycles 按时间升序的完整周期，少于 2 个时显示空状态
 * @param forecast 预测结果，提供周期中位数与规律性
 * @param currentYear 今年，跨年的日期标签带上年份
 * @param modifier 布局修饰符
 */
@Composable
internal fun PeriodCycleTrendCard(
    cycles: List<CycleLength>,
    forecast: PeriodForecast,
    currentYear: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = PeriodCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth().testTag("period_cycle_trend"),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("周期长度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (cycles.size < 2) {
                Text(
                    text = "记录满 2 个完整周期后，这里会显示周期长度的变化",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val regularity = forecast.regularity.takeIf { it != PeriodRegularity.INSUFFICIENT }?.let { " · ${it.label}" }.orEmpty()
            Text(
                text = "最近 ${cycles.size} 个周期 · 约 ${forecast.cycleLength} 天$regularity",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CycleTrendChart(
                cycles = cycles,
                currentYear = currentYear,
                surface = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().height(184.dp).padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun CycleTrendChart(
    cycles: List<CycleLength>,
    currentYear: Int,
    surface: Color,
    modifier: Modifier = Modifier,
) {
    val lineColor = if (surface.luminance() < 0.5f) DarkTrendColor else periodPalette().period
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisText = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val valueText = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    val tooltipColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipText = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.inverseOnSurface)
    val measurer = rememberTextMeasurer()

    // 数据变化时重新从左到右描线
    val progress = remember { Animatable(0f) }
    LaunchedEffect(cycles) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    var selected by remember(cycles) { mutableStateOf<Int?>(null) }
    val tooltipAlpha by animateFloatAsState(targetValue = if (selected != null) 1f else 0f, label = "trendTooltip")
    var shownIndex by remember(cycles) { mutableStateOf<Int?>(null) }
    if (selected != null) shownIndex = selected

    val ticks = remember(cycles) { cycleAxisTicks(cycles.map { it.days }) }
    val description =
        remember(cycles) {
            "周期长度趋势：" + cycles.joinToString("，") { "${it.start.month.number}月${it.start.day}日开始 ${it.days} 天" }
        }

    Canvas(
        modifier =
        modifier
            .semantics { contentDescription = description }
            .pointerInput(cycles) {
                detectTapGestures { offset ->
                    val layout = chartLayout(size.width.toFloat(), size.height.toFloat(), cycles.size, measurer, axisText)
                    val index = (0 until cycles.size).minBy { abs(layout.x(it) - offset.x) }
                    selected = if (selected == index) null else index
                }
            },
    ) {
        val layout = chartLayout(size.width, size.height, cycles.size, measurer, axisText)
        val minTick = ticks.first().toFloat()
        val maxTick = ticks.last().toFloat()
        fun y(days: Int) = layout.top + (1f - (days - minTick) / (maxTick - minTick)) * (layout.bottom - layout.top)

        // 纵轴：发丝级网格线，刻度文字在左侧
        ticks.forEach { tick ->
            val ty = y(tick)
            drawLine(gridColor, Offset(layout.left, ty), Offset(size.width, ty), strokeWidth = 1.dp.toPx())
            val label = measurer.measure("$tick", axisText)
            drawText(label, topLeft = Offset(layout.left - 8.dp.toPx() - label.size.width, ty - label.size.height / 2f))
        }

        // 横轴：周期开始日（月/日，同月两次开始也能区分）；点太密时从最近一个往前隔一个显示
        val sampleWidth = measurer.measure("12/30", axisText).size.width
        val every = if (layout.x(1) - layout.x(0) < sampleWidth + 6.dp.toPx()) 2 else 1
        cycles.forEachIndexed { index, cycle ->
            if ((cycles.lastIndex - index) % every != 0) return@forEachIndexed
            val label = measurer.measure("${cycle.start.month.number}/${cycle.start.day}", axisText)
            drawText(label, topLeft = Offset(layout.x(index) - label.size.width / 2f, layout.bottom + 8.dp.toPx()))
        }

        val points = cycles.mapIndexed { index, cycle -> Offset(layout.x(index), y(cycle.days)) }
        val line =
            Path().apply {
                points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
            }
        val measure = PathMeasure().apply { setPath(line, false) }
        val drawn = Path().also { measure.getSegment(0f, measure.length * progress.value, it, true) }
        val revealX = points.first().x + (points.last().x - points.first().x) * progress.value

        // 线下 10% 透明度的面积，随描线进度一起展开
        val area =
            Path().apply {
                addPath(drawn)
                lineTo(revealX, layout.bottom)
                lineTo(points.first().x, layout.bottom)
                close()
            }
        drawPath(area, lineColor.copy(alpha = 0.10f))
        drawPath(drawn, lineColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 数据点：带 2dp 卡片底色描边，描线经过后出现
        points.forEachIndexed { index, point ->
            if (point.x > revealX + 0.5f) return@forEachIndexed
            val radius = if (index == selected) 6.dp.toPx() else 4.dp.toPx()
            drawCircle(surface, radius = radius + 2.dp.toPx(), center = point)
            drawCircle(lineColor, radius = radius, center = point)
        }

        // 只给最近一个周期直接标数值，其余交给点击提示
        if (progress.value >= 1f && selected == null) {
            val last = points.last()
            val label = measurer.measure("${cycles.last().days} 天", valueText)
            drawText(
                label,
                topLeft = Offset((last.x - label.size.width / 2f).coerceAtMost(size.width - label.size.width), last.y - label.size.height - 10.dp.toPx()),
            )
        }

        shownIndex?.let { index ->
            if (tooltipAlpha > 0f) drawTooltip(points[index], cycles[index], currentYear, measurer, tooltipText, tooltipColor, tooltipAlpha)
        }
    }
}

private data class ChartLayout(
    val left: Float,
    val top: Float,
    val bottom: Float,
    val right: Float,
    val count: Int,
) {
    fun x(index: Int): Float = if (count <= 1) (left + right) / 2f else left + (right - left) * index / (count - 1)
}

/** 绘制与点击共用的坐标布局：左侧留刻度文字，顶部留数值标签与提示框，底部留月份。 */
private fun Density.chartLayout(
    width: Float,
    height: Float,
    count: Int,
    measurer: TextMeasurer,
    axisText: TextStyle,
): ChartLayout {
    val tickWidth = measurer.measure("88", axisText).size.width
    val axisHeight = measurer.measure("9/1", axisText).size.height
    return ChartLayout(
        left = tickWidth + 18.dp.toPx(),
        top = 28.dp.toPx(),
        bottom = height - axisHeight - 8.dp.toPx(),
        right = width - 16.dp.toPx(),
        count = count,
    )
}

private fun DrawScope.drawTooltip(
    point: Offset,
    cycle: CycleLength,
    currentYear: Int,
    measurer: TextMeasurer,
    style: TextStyle,
    background: Color,
    alpha: Float,
) {
    val label = measurer.measure("${cycle.start.periodLabel(currentYear)}起 · ${cycle.days} 天", style)
    val padH = 10.dp.toPx()
    val padV = 6.dp.toPx()
    val boxWidth = label.size.width + padH * 2
    val boxHeight = label.size.height + padV * 2
    val left = (point.x - boxWidth / 2f).coerceIn(0f, size.width - boxWidth)
    val top = (point.y - boxHeight - 12.dp.toPx()).coerceAtLeast(0f)
    drawRoundRect(background.copy(alpha = alpha), topLeft = Offset(left, top), size = Size(boxWidth, boxHeight), cornerRadius = CornerRadius(8.dp.toPx()))
    drawText(label, topLeft = Offset(left + padH, top + padV), alpha = alpha)
}
