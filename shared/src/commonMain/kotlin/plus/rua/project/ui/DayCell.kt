package plus.rua.project.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyme.solar.SolarDay
import kotlinx.datetime.LocalDate

enum class DayCellState {
    NORMAL, OTHER_MONTH, TODAY, SELECTED, SELECTED_TODAY
}

/**
 * 单个日期单元格，显示日期数字并支持选中/今天/非当月状态。
 *
 * @param date 日期
 * @param isCurrentMonth 是否属于当前显示月份
 * @param isSelected 是否为选中日期
 * @param isToday 是否为今天
 * @param onClick 点击回调
 * @param modifier 外部布局修饰符
 */
@Composable
fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentState = when {
        isSelected && isToday -> DayCellState.SELECTED_TODAY
        isSelected -> DayCellState.SELECTED
        isToday -> DayCellState.TODAY
        !isCurrentMonth -> DayCellState.OTHER_MONTH
        else -> DayCellState.NORMAL
    }

    val transition = updateTransition(targetState = currentState, label = "dayCell")

    val revealProgress by transition.animateFloat(
        transitionSpec = { tween(250, easing = FastOutSlowInEasing) },
        label = "revealProgress"
    ) { state ->
        when (state) {
            DayCellState.SELECTED, DayCellState.SELECTED_TODAY -> 1f
            else -> 0f
        }
    }

    val contentColor by transition.animateColor(
        transitionSpec = { tween(250, easing = FastOutSlowInEasing) },
        label = "contentColor"
    ) { state ->
        when (state) {
            DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer
            DayCellState.SELECTED -> MaterialTheme.colorScheme.onPrimary
            DayCellState.TODAY -> MaterialTheme.colorScheme.primary
            DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            DayCellState.NORMAL -> MaterialTheme.colorScheme.onSurface
        }
    }

    val selectedColor by transition.animateColor(
        transitionSpec = { tween(250, easing = FastOutSlowInEasing) },
        label = "selectedColor"
    ) { state ->
        when (state) {
            DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.primaryContainer
            DayCellState.SELECTED -> MaterialTheme.colorScheme.primary
            else -> Color.Transparent
        }
    }

    val borderAlpha by transition.animateFloat(
        transitionSpec = { tween(250, easing = FastOutSlowInEasing) },
        label = "borderAlpha"
    ) { state ->
        when (state) {
            DayCellState.TODAY -> 1.5f
            else -> 0f
        }
    }

    val todayBorderColor = MaterialTheme.colorScheme.primary

    data class DayAnnotation(val text: String, val isHighlight: Boolean)

    val annotation = remember(date) {
        val solarDay = SolarDay.fromYmd(date.year, date.monthNumber, date.day)
        val lunarDay = solarDay.getLunarDay()

        // 法定假日优先
        val legalHoliday = solarDay.getLegalHoliday()
        if (legalHoliday != null) {
            val suffix = if (legalHoliday.isWork()) "班" else "休"
            return@remember DayAnnotation("${legalHoliday.getName()}$suffix", true)
        }

        // 农历传统节日
        val lunarFestival = lunarDay.getFestival()
        if (lunarFestival != null) {
            return@remember DayAnnotation(lunarFestival.getName(), true)
        }

        // 节气（当天才显示）
        val termDay = solarDay.getTermDay()
        if (termDay.getDayIndex() == 0) {
            return@remember DayAnnotation(termDay.getSolarTerm().getName(), true)
        }

        // 公历节日
        val solarFestival = solarDay.getFestival()
        if (solarFestival != null) {
            return@remember DayAnnotation(solarFestival.getName(), true)
        }

        // 默认：农历日期
        val name = lunarDay.getName()
        val text = if (name == "初一") {
            val lunarMonth = lunarDay.getLunarMonth()
            "${lunarMonth.getName()}月"
        } else {
            name
        }
        DayAnnotation(text, false)
    }

    val lunarColor by transition.animateColor(
        transitionSpec = { tween(250, easing = FastOutSlowInEasing) },
        label = "lunarColor"
    ) { state ->
        if (annotation.isHighlight) {
            when (state) {
                DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                DayCellState.SELECTED -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                DayCellState.TODAY -> MaterialTheme.colorScheme.primary
                DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                DayCellState.NORMAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            }
        } else {
            when (state) {
                DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                DayCellState.SELECTED -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                DayCellState.TODAY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
                DayCellState.NORMAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics {
                @Suppress("DEPRECATION")
                contentDescription = "${date.year}年${date.monthNumber}月${date.day}日"
            }
            .clip(CircleShape)
            .drawBehind {
                if (revealProgress > 0f) {
                    val maxRadius = size.minDimension / 2f
                    drawCircle(
                        color = selectedColor,
                        radius = revealProgress * maxRadius,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                }
                if (borderAlpha > 0f) {
                    drawCircle(
                        color = todayBorderColor.copy(alpha = borderAlpha.coerceAtMost(1f)),
                        radius = size.minDimension / 2f,
                        center = Offset(size.width / 2f, size.height / 2f),
                        style = Stroke(width = borderAlpha.coerceAtMost(1.5f) * 1.5.dp.toPx())
                    )
                }
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date.day.toString(),
                textAlign = TextAlign.Center,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = annotation.text,
                textAlign = TextAlign.Center,
                color = lunarColor,
                fontSize = 7.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                lineHeight = 9.sp
            )
        }
    }
}
