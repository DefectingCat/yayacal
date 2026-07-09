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
import plus.rua.project.RephaseFlip
import plus.rua.project.ShiftKind
import plus.rua.project.ShiftPattern

/**
 * 班次设置页用的迷你月历。点某天翻转班/休(仅当天),长按翻转并从次日起重排周期。
 *
 * 月份可前后翻页,每格显示日期数字与班次角标(班/休/起)。
 * 点击翻转该天的班/休 override;长按翻转该天并在次日插入重排起点,
 * 后续按 cycle 重新顺延。再次长按同一天可撤销。
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
                                onLongClick = { onPatternChange(toggleFlipAndRephase(pattern, date)) },
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
 * 翻转某天的班/休(单日 override)。翻转后若与基础周期值一致,则移除 override。
 *
 * 若该天存在 rephaseFlip(长按产生的翻转并重排),则移除整个 rephaseFlip,
 * 避免留下孤立的"幽灵重排"(只清翻转但保留后续相位重排)。
 */
private fun toggleOverride(pattern: ShiftPattern, date: LocalDate): ShiftPattern {
    val existingFlip = pattern.rephaseFlips.find { it.date == date }
    if (existingFlip != null) {
        // 该天是 rephaseFlip 的翻转日:整体移除,避免幽灵重排
        return pattern.copy(rephaseFlips = pattern.rephaseFlips - existingFlip)
    }
    val current = pattern.kindAt(date) ?: return pattern
    val newVal = if (current == ShiftKind.WORK) ShiftKind.OFF else ShiftKind.WORK
    val tempPattern = pattern.copy(overrides = pattern.overrides - date)
    val base = tempPattern.kindAt(date)
    val newOverrides = if (newVal == base) pattern.overrides - date
                       else pattern.overrides + (date to newVal)
    return pattern.copy(overrides = newOverrides)
}

/**
 * 翻转某天的班/休,并从次日起重排周期(后续按 cycle 重新顺延)。
 *
 * 产出原子记录 [RephaseFlip]:翻转该天 + 从次日([rephaseFrom])起重排。
 * 次日成为新的 cycle[0],后续自动按周期顺延。
 *
 * 撤销:再次长按同一天,按 [RephaseFlip.date] 精确匹配并整体移除(不会误删其他记录)。
 *
 * @param date 被翻转并作为重排起点的日期
 */
private fun toggleFlipAndRephase(pattern: ShiftPattern, date: LocalDate): ShiftPattern {
    val existing = pattern.rephaseFlips.find { it.date == date }
    if (existing != null) {
        // 撤销:整体移除该 rephaseFlip(原子删除)
        return pattern.copy(rephaseFlips = pattern.rephaseFlips - existing)
    }

    // 翻转该天 + 次日重排
    val current = pattern.kindAt(date) ?: return pattern
    val newVal = if (current == ShiftKind.WORK) ShiftKind.OFF else ShiftKind.WORK
    val rephaseFrom = date.plus(DatePeriod(days = 1))
    return pattern.copy(
        overrides = pattern.overrides - date,  // 该天改由 rephaseFlip 管辖,移除可能的旧 override
        rephaseFlips = pattern.rephaseFlips + RephaseFlip(date, newVal, rephaseFrom)
    )
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
    // rephaseFlip 的 rephaseFrom = 当天,表示该天为重排起点
    val isRephaseStart = pattern.rephaseFlips.any { it.rephaseFrom == date }

    val badgeColor = when {
        isRephaseStart -> MaterialTheme.colorScheme.tertiary
        kind == ShiftKind.WORK -> MaterialTheme.colorScheme.primary
        kind == ShiftKind.OFF -> MaterialTheme.colorScheme.error
        else -> Color.Transparent
    }
    val badgeText = when {
        isRephaseStart -> "起"
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
