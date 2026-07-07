package plus.rua.project.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import plus.rua.project.PhaseBreak
import plus.rua.project.ShiftKind
import plus.rua.project.ShiftPattern

/**
 * 班次设置页用的迷你月历。点某天翻转班/休,长按设/清相位断点。
 *
 * 月份可前后翻页,每格显示日期数字与班次角标(班/休/断)。
 * 点击翻转该天的班/休 override;长按切换相位断点。
 *
 * @param pattern 当前轮班配置(只读),由 [onPatternChange] 修改
 * @param onPatternChange 修改后的新 pattern 回调
 * @param modifier 外部布局修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShiftCalendarGrid(
    pattern: ShiftPattern,
    onPatternChange: (ShiftPattern) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var viewYear by remember { mutableStateOf(today.year) }
    var viewMonth by remember { mutableStateOf(today.month.number) }

    val firstOfMonth = LocalDate(viewYear, Month(viewMonth), 1)
    val daysInMonth = firstOfMonth.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
    val firstWeekdayOffset = firstOfMonth.dayOfWeek.isoDayNumber - 1

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (viewMonth == 1) { viewMonth = 12; viewYear -= 1 } else viewMonth -= 1
                }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "上个月")
                }
                Text(
                    text = "${viewYear}年 ${viewMonth}月",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = {
                    if (viewMonth == 12) { viewMonth = 1; viewYear += 1 } else viewMonth += 1
                }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "下个月")
                }
            }

            val weekTitles = listOf("一", "二", "三", "四", "五", "六", "日")
            Row(Modifier.fillMaxWidth()) {
                weekTitles.forEach { w ->
                    Text(
                        text = w,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            (0 until 6).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    (0 until 7).forEach { col ->
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - firstWeekdayOffset + 1
                        if (dayNum in 1..daysInMonth) {
                            val date = LocalDate(viewYear, Month(viewMonth), dayNum)
                            ShiftDayCell(
                                date = date,
                                pattern = pattern,
                                onClick = { onPatternChange(toggleOverride(pattern, date)) },
                                onLongClick = { onPatternChange(togglePhaseBreak(pattern, date)) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * 翻转某天的班/休 override。翻转后若与基础周期值一致,则移除 override。
 */
private fun toggleOverride(pattern: ShiftPattern, date: LocalDate): ShiftPattern {
    val current = pattern.kindAt(date) ?: return pattern
    val newVal = if (current == ShiftKind.WORK) ShiftKind.OFF else ShiftKind.WORK
    val tempPattern = pattern.copy(overrides = pattern.overrides - date)
    val base = tempPattern.kindAt(date)
    val newOverrides = if (newVal == base) pattern.overrides - date
                       else pattern.overrides + (date to newVal)
    return pattern.copy(overrides = newOverrides)
}

/**
 * 切换某天的相位断点:已有则清除,没有则新增(cycleOffset=0)。
 */
private fun togglePhaseBreak(pattern: ShiftPattern, date: LocalDate): ShiftPattern {
    val existing = pattern.phaseBreaks.find { it.date == date }
    return if (existing != null) {
        pattern.copy(phaseBreaks = pattern.phaseBreaks - existing)
    } else {
        pattern.copy(phaseBreaks = pattern.phaseBreaks + PhaseBreak(date, 0))
    }
}

/**
 * 迷你月历的单日格子。显示日期数字与班次角标。
 *
 * @param date 该格对应的日期
 * @param pattern 当前轮班配置(只读)
 * @param onClick 点击回调(翻转班/休)
 * @param onLongClick 长按回调(设/清相位断点)
 * @param modifier 外部布局修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShiftDayCell(
    date: LocalDate,
    pattern: ShiftPattern,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kind = pattern.kindAt(date)
    val isBreak = pattern.phaseBreaks.any { it.date == date }

    val badgeColor = when {
        isBreak -> MaterialTheme.colorScheme.tertiary
        kind == ShiftKind.WORK -> MaterialTheme.colorScheme.primary
        kind == ShiftKind.OFF -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val badgeText = when {
        isBreak -> "断"
        kind == ShiftKind.WORK -> "班"
        kind == ShiftKind.OFF -> "休"
        else -> ""
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.day.toString(),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (badgeText.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .background(badgeColor, CircleShape)
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeText,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp
                )
            }
        }
    }
}
