package plus.rua.project.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tyme.solar.SolarDay
import kotlinx.datetime.LocalDate
import plus.rua.project.ShiftKind

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
 * @param shiftKind 个人轮班类型;null 表示不显示。与法定调休完全独立。
 * @param showLegalHoliday 是否显示法定调休角标。
 *   false(默认):排班放右上角,左上角空白,不显示法定调休。
 *   true:排班放左上角,法定调休放右上角(旧版布局)。
 * @param onClick 点击回调
 * @param modifier 外部布局修饰符
 */
@Composable
fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    shiftKind: ShiftKind?,
    showLegalHoliday: Boolean,
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
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "revealProgress"
    ) { state ->
        when (state) {
            DayCellState.SELECTED, DayCellState.SELECTED_TODAY -> 1f
            else -> 0f
        }
    }

    val contentColor by transition.animateColor(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "contentColor"
    ) { state ->
        when (state) {
            DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer
            DayCellState.SELECTED -> MaterialTheme.colorScheme.primary
            DayCellState.TODAY -> MaterialTheme.colorScheme.primary
            DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            DayCellState.NORMAL -> MaterialTheme.colorScheme.onSurface
        }
    }

    // 选中今天:实心填充 primaryContainer;其他状态不填充。
    val selectedFillColor by transition.animateColor(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "selectedFillColor"
    ) { state ->
        when (state) {
            DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }
    }

    // 选中非今天:绘制描边圆,避免遮挡右上角角标。
    val selectedOutlineAlpha by transition.animateFloat(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "selectedOutlineAlpha"
    ) { state ->
        when (state) {
            DayCellState.SELECTED -> 1f
            else -> 0f
        }
    }

    val selectedOutlineColor = MaterialTheme.colorScheme.primary

    data class DayAnnotation(val text: String, val isHighlight: Boolean)

    val holidayBadge = remember(date) {
        @Suppress("DEPRECATION") // monthNumber 无替代 API
        val solarDay = SolarDay.fromYmd(date.year, date.monthNumber, date.day)
        solarDay.getLegalHoliday()?.let { if (it.isWork()) "班" else "休" }
    }

    val annotation = remember(date) {
        @Suppress("DEPRECATION") // monthNumber 无替代 API
        val solarDay = SolarDay.fromYmd(date.year, date.monthNumber, date.day)
        val lunarDay = solarDay.getLunarDay()

        // 农历传统节日（仅当天）
        val lunarFestival = lunarDay.getFestival()
        if (lunarFestival != null) {
            return@remember DayAnnotation(lunarFestival.getName(), true)
        }

        // 节气（当天才显示）
        val termDay = solarDay.getTermDay()
        if (termDay.getDayIndex() == 0) {
            return@remember DayAnnotation(termDay.getSolarTerm().getName(), true)
        }

        // 公历节日（仅当天）
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
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "lunarColor"
    ) { state ->
        if (annotation.isHighlight) {
            when (state) {
                DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.85f
                )

                DayCellState.SELECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                DayCellState.TODAY -> MaterialTheme.colorScheme.primary
                DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                DayCellState.NORMAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            }
        } else {
            when (state) {
                DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.7f
                )

                DayCellState.SELECTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                DayCellState.TODAY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
                DayCellState.NORMAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
        }
    }

    val holidayBadgeColor = when (holidayBadge) {
        "休" -> MaterialTheme.colorScheme.error
        "班" -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val holidayBadgeAlpha = if (isCurrentMonth) 1f else 0.38f

    Box(
        modifier = modifier.aspectRatio(1f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    @Suppress("DEPRECATION")
                    contentDescription = "${date.year}年${date.monthNumber}月${date.day}日"
                }
                .clip(CircleShape)
                .drawBehind {
                    val maxRadius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    if (revealProgress > 0f && selectedFillColor.alpha > 0f) {
                        drawCircle(
                            color = selectedFillColor,
                            radius = revealProgress * maxRadius,
                            center = center
                        )
                    }
                    if (revealProgress > 0f && selectedOutlineAlpha > 0f) {
                        val strokePx = 1.5.dp.toPx()
                        drawCircle(
                            color = selectedOutlineColor.copy(alpha = selectedOutlineAlpha),
                            radius = revealProgress * maxRadius - strokePx / 2f,
                            center = center,
                            style = Stroke(width = strokePx)
                        )
                    }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
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
        if (shiftKind != null) {
            val shiftAccentColor = if (shiftKind == ShiftKind.WORK) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            val shiftOnAccentColor = if (shiftKind == ShiftKind.WORK) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onError
            }
            val shiftLabel = if (shiftKind == ShiftKind.WORK) "班" else "休"
            val shiftAlpha = if (isCurrentMonth) 1f else 0.38f
            // 右上角(默认)沿用法定调休视觉:surface 背景 + 彩色文字;
            // 左上角(showLegalHoliday=true 时)用实心胶囊,与右上角法定调休区分。
            val shiftBgColor =
                if (showLegalHoliday) shiftAccentColor else MaterialTheme.colorScheme.surface
            val shiftFgColor = if (showLegalHoliday) shiftOnAccentColor else shiftAccentColor
            val shiftAlignment = if (showLegalHoliday) Alignment.TopStart else Alignment.TopEnd
            val shiftPadding = if (showLegalHoliday) {
                Modifier.padding(top = 1.dp, start = 2.dp)
            } else {
                Modifier.padding(top = 1.dp, end = 2.dp)
            }
            Text(
                text = shiftLabel,
                color = shiftFgColor.copy(alpha = shiftAlpha),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 9.sp,
                modifier = Modifier
                    .align(shiftAlignment)
                    .zIndex(1f)
                    .then(shiftPadding)
                    .background(shiftBgColor.copy(alpha = shiftAlpha), CircleShape)
                    .padding(horizontal = 2.dp)
            )
        }
        if (showLegalHoliday && holidayBadge != null) {
            Text(
                text = holidayBadge,
                color = holidayBadgeColor.copy(alpha = holidayBadgeAlpha),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f)
                    .padding(top = 1.dp, end = 2.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .padding(horizontal = 2.dp)
            )
        }
    }
}
