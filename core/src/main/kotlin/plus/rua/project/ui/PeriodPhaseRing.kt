package plus.rua.project.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlinx.datetime.daysUntil
import plus.rua.project.CyclePhase
import plus.rua.project.CycleSpan
import plus.rua.project.PeriodDayInfo
import plus.rua.project.shared.R
import kotlin.math.cos
import kotlin.math.sin

/** 圆环外侧留给阶段文字的宽度。 */
private val LabelSpace = 28.dp
private val RingStroke = 18.dp
private const val SEGMENT_GAP_DEGREES = 3f

/** 阶段弧线短于这个角度时不写文字，避免和相邻的排卵日文字挤在一起。 */
private const val MIN_LABEL_SWEEP = 24f

/** 周期内的一段阶段，按天计 [fromDay, toDay)，第 0 天为经期开始日。 */
private data class PhaseSegment(
    val phase: CyclePhase,
    val fromDay: Int,
    val toDay: Int,
)

private fun CycleSpan.segments(): List<PhaseSegment> {
    val ovulationDay = ovulation?.let { start.daysUntil(it) }
    return buildList {
        add(PhaseSegment(CyclePhase.MENSTRUAL, 0, periodDays))
        if (ovulationDay == null) {
            if (length > periodDays) add(PhaseSegment(CyclePhase.LUTEAL, periodDays, length))
            return@buildList
        }
        if (ovulationDay > periodDays) add(PhaseSegment(CyclePhase.FOLLICULAR, periodDays, ovulationDay))
        add(PhaseSegment(CyclePhase.OVULATION, ovulationDay, ovulationDay + 1))
        if (length > ovulationDay + 1) add(PhaseSegment(CyclePhase.LUTEAL, ovulationDay + 1, length))
    }
}

@DrawableRes
private fun duckFor(info: PeriodDayInfo): Int = when (info) {
    is PeriodDayInfo.NoRecord -> {
        R.drawable.period_duck_empty
    }

    is PeriodDayInfo.Late -> {
        R.drawable.period_duck_late
    }

    is PeriodDayInfo.InPhase -> {
        when (info.phase) {
            CyclePhase.MENSTRUAL -> R.drawable.period_duck_menstrual
            CyclePhase.FOLLICULAR -> R.drawable.period_duck_follicular
            CyclePhase.OVULATION -> R.drawable.period_duck_ovulation
            CyclePhase.LUTEAL -> R.drawable.period_duck_luteal
        }
    }
}

/** 圆心为 [center]、半径 [radius] 的圆上，从 3 点钟方向顺时针 [degrees] 度处的点。 */
private fun pointOn(
    center: Offset,
    radius: Float,
    degrees: Float,
): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
}

/**
 * 今日周期卡片里的阶段圆环：从 12 点钟方向顺时针依次为月经期、卵泡期、排卵日、黄体期，每天占相同角度。
 * 当天所在阶段用实色并按天画小圆点，其余阶段为淡色；指针从中间的鸭鸭指向当天，推迟时停在周期最后一天。
 * 首次显示时指针从 12 点钟方向转到当天。
 *
 * @param info 当天的周期解读；没有记录时只画灰色空环
 * @param modifier 布局修饰符，圆环按宽度取正方形
 */
@Composable
internal fun PeriodPhaseRing(
    info: PeriodDayInfo,
    modifier: Modifier = Modifier,
) {
    val palette = periodPalette()
    val cycle =
        when (info) {
            is PeriodDayInfo.InPhase -> info.cycle
            is PeriodDayInfo.Late -> info.cycle
            is PeriodDayInfo.NoRecord -> null
        }
    val activePhase = (info as? PeriodDayInfo.InPhase)?.phase
    val accent =
        when (info) {
            is PeriodDayInfo.InPhase -> palette.phase(info.phase)
            is PeriodDayInfo.Late -> palette.period
            is PeriodDayInfo.NoRecord -> MaterialTheme.colorScheme.outline
        }
    val segments = remember(cycle) { cycle?.segments().orEmpty() }
    // 指针相对 12 点钟方向的角度，指向当天那一格的中间
    val targetAngle =
        when (info) {
            is PeriodDayInfo.InPhase -> (info.cycle.start.daysUntil(info.date) + 0.5f) * 360f / info.cycle.length
            is PeriodDayInfo.Late -> (info.cycle.length - 0.5f) * 360f / info.cycle.length
            is PeriodDayInfo.NoRecord -> null
        }
    val pointer = remember { Animatable(0f) }
    LaunchedEffect(targetAngle) {
        if (targetAngle != null) pointer.animateTo(targetAngle, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    val breathing = rememberInfiniteTransition(label = "duckBreathing")
    val duckScale by breathing.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "duckScale",
    )
    val flower = painterResource(R.drawable.period_ovulation_flower)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium
    val inactiveLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val knobFill = MaterialTheme.colorScheme.surfaceContainerLowest
    val emptyTrack = MaterialTheme.colorScheme.surfaceContainerHighest

    BoxWithConstraints(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        val ringRadius = maxWidth / 2 - LabelSpace - RingStroke / 2
        val innerRadius = ringRadius - RingStroke / 2 - 14.dp
        Canvas(Modifier.fillMaxSize()) {
            val radius = ringRadius.toPx()
            val inner = innerRadius.toPx()
            val stroke = RingStroke.toPx()
            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0.03f)), center, inner),
                radius = inner,
            )
            if (cycle == null) {
                drawCircle(emptyTrack, radius, style = Stroke(stroke))
                return@Canvas
            }
            val dayDegrees = 360f / cycle.length
            // 圆头线帽会向两端各伸出半个线宽，换算成角度从弧长里扣掉
            val capDegrees = Math.toDegrees((stroke / 2 / radius).toDouble()).toFloat()
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2, radius * 2)
            segments.filter { it.phase != CyclePhase.OVULATION }.forEach { segment ->
                val color = palette.phase(segment.phase)
                val active = segment.phase == activePhase
                val padding = SEGMENT_GAP_DEGREES / 2 + capDegrees
                val fullSweep = (segment.toDay - segment.fromDay) * dayDegrees
                val sweep = fullSweep - padding * 2
                // 太短的阶段画成一个圆点
                val startAngle = if (sweep > 0f) -90f + segment.fromDay * dayDegrees + padding else -90f + (segment.fromDay * dayDegrees + fullSweep / 2)
                val drawSweep = sweep.coerceAtLeast(0.01f)
                if (active) {
                    drawArc(color.copy(alpha = 0.18f), startAngle, drawSweep, false, arcTopLeft, arcSize, style = Stroke(stroke + 10.dp.toPx(), cap = StrokeCap.Round))
                }
                drawArc(if (active) color else color.copy(alpha = 0.22f), startAngle, drawSweep, false, arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                if (active) {
                    for (day in segment.fromDay until segment.toDay) {
                        drawCircle(Color.White.copy(alpha = 0.9f), 2.5.dp.toPx(), pointOn(center, radius, -90f + (day + 0.5f) * dayDegrees))
                    }
                }
            }
            segments.forEach { segment ->
                val sweep = (segment.toDay - segment.fromDay) * dayDegrees
                val active = segment.phase == activePhase
                if (segment.phase != CyclePhase.OVULATION && sweep < MIN_LABEL_SWEEP && !active) return@forEach
                drawPhaseLabel(
                    layout =
                    textMeasurer.measure(
                        segment.phase.label,
                        labelStyle.copy(
                            color = if (active) palette.phase(segment.phase) else inactiveLabelColor,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        ),
                    ),
                    radius = radius + stroke / 2 + 4.dp.toPx(),
                    degrees = -90f + (segment.fromDay * dayDegrees + sweep / 2),
                )
            }

            val angle = -90f + pointer.value
            val knob = pointOn(center, radius, angle)
            val knobRadius = stroke / 2 + 3.dp.toPx()
            drawLine(accent, pointOn(center, inner * 0.62f, angle), pointOn(center, radius - knobRadius, angle), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(knobFill, knobRadius, knob)
            drawCircle(accent, knobRadius, knob, style = Stroke(3.dp.toPx()))
            drawCircle(accent, 3.dp.toPx(), knob)

            // 排卵日小花最后画；排卵日当天放大并盖住指针端点
            segments.firstOrNull { it.phase == CyclePhase.OVULATION }?.let { segment ->
                val active = activePhase == CyclePhase.OVULATION
                val flowerSize = stroke + if (active) 18.dp.toPx() else 10.dp.toPx()
                val position = pointOn(center, radius, -90f + (segment.fromDay + 0.5f) * dayDegrees)
                translate(position.x - flowerSize / 2, position.y - flowerSize / 2) {
                    with(flower) { draw(Size(flowerSize, flowerSize), alpha = if (active) 1f else 0.6f) }
                }
            }
        }
        Image(
            painter = painterResource(duckFor(info)),
            contentDescription = null,
            modifier =
            Modifier
                .size(innerRadius * 2 * 0.9f)
                .graphicsLayer {
                    scaleX = duckScale
                    scaleY = duckScale
                },
        )
    }
}

/** 在圆环外侧沿切线方向写阶段名；下半圈翻转 180° 保持文字正立。 */
private fun DrawScope.drawPhaseLabel(
    layout: TextLayoutResult,
    radius: Float,
    degrees: Float,
) {
    val position = pointOn(center, radius + layout.size.height / 2f, degrees)
    val normalized = (degrees % 360f + 360f) % 360f
    val rotation = if (normalized <= 180f) degrees - 90f else degrees + 90f
    rotate(rotation, position) {
        drawText(layout, topLeft = Offset(position.x - layout.size.width / 2f, position.y - layout.size.height / 2f))
    }
}
