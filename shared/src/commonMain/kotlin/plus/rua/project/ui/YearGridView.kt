package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * 年视图 4×3 月历网格。
 *
 * @param year 显示的年份
 * @param selectedMonth 当前选中月份（1-12）
 * @param today 今天的日期
 * @param onMonthClick 月份点击回调
 * @param modifier 外部布局修饰符
 */
@Composable
fun YearGridView(
    year: Int,
    selectedMonth: Int,
    today: LocalDate,
    onMonthClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 4×3 月历网格
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            (0 until 4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    (0 until 3).forEach { col ->
                        val month = row * 3 + col + 1
                        MiniMonth(
                            year = year,
                            month = month,
                            isSelected = month == selectedMonth,
                            today = today,
                            onClick = { onMonthClick(month) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 精简版月历：月份标题 + 星期行 + 日期数字网格。
 */
@Composable
private fun MiniMonth(
    year: Int,
    month: Int,
    isSelected: Boolean,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val days = remember(year, month) { generateMiniMonthDays(year, month) }
    val titleColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val weekdayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    val dayColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val otherMonthColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    val todayBgColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .padding(2.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 月份标题
        Text(
            text = "${month}月",
            color = titleColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        // 星期行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    color = weekdayColor,
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // 日期网格
        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                week.forEach { dayData ->
                    val isToday = dayData.date == today && dayData.isCurrentMonth
                    val color = when {
                        !dayData.isCurrentMonth -> otherMonthColor
                        isToday -> MaterialTheme.colorScheme.onPrimary
                        else -> dayColor
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isToday) {
                            Box(
                                modifier = Modifier
                                    .drawBehind {
                                        drawCircle(
                                            color = todayBgColor,
                                            radius = size.minDimension / 2f,
                                            center = Offset(size.width / 2f, size.height / 2f)
                                        )
                                    }
                                    .clip(CircleShape)
                            )
                        }
                        Text(
                            text = if (dayData.isCurrentMonth) dayData.date.day.toString() else "",
                            color = color,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}

private data class MiniDayData(
    val date: LocalDate,
    val isCurrentMonth: Boolean
)

@Suppress("DEPRECATION") // monthNumber 无替代 API
private fun generateMiniMonthDays(year: Int, month: Int): List<MiniDayData> {
    val firstOfMonth = LocalDate(year, month, 1)
    val offset = firstOfMonth.dayOfWeek.ordinal
    val startDate = firstOfMonth.minus(DatePeriod(days = offset))
    val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
    val daysInMonth = nextMonth.minus(DatePeriod(days = 1)).day
    val rows = ((offset + daysInMonth - 1) / 7) + 1
    val totalDays = rows * 7

    return (0 until totalDays).map { i ->
        val date = startDate.plus(DatePeriod(days = i))
        MiniDayData(
            date = date,
            isCurrentMonth = date.month.number == month && date.year == year
        )
    }
}

/**
 * 年视图标题栏，显示年份文字和左右导航箭头。
 *
 * 年份切换时文字有垂直滑动过渡动画，方向由新旧年份大小决定。
 *
 * @param year 当前年份
 * @param onYearChange 年份切换回调
 * @param modifier 外部布局修饰符
 */
@Composable
fun YearHeader(
    year: Int,
    onYearChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onYearChange(year - 1) }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = year,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInVertically(tween(250)) { -it } togetherWith
                            slideOutVertically(tween(250)) { it }
                    } else {
                        slideInVertically(tween(250)) { it } togetherWith
                            slideOutVertically(tween(250)) { -it }
                    }
                }
            ) { y ->
                Text(
                    text = "${y}年",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = "›",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable { onYearChange(year + 1) }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
