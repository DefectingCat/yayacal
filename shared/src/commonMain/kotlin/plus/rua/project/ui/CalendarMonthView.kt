package plus.rua.project.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import plus.rua.project.CalendarViewModel

/**
 * 日历主界面，包含月/周视图切换和折叠动画。
 *
 * 折叠时日历从月视图（6行）收缩为周视图（1行），BottomCard 同步上移填充空间。
 *
 * @param modifier 外部布局修饰符
 */
@Composable
fun CalendarMonthView(
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { CalendarViewModel(coroutineScope) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    var currentYear by remember { mutableIntStateOf(viewModel.currentYear) }
    var currentMonth by remember { mutableIntStateOf(viewModel.currentMonth) }
    val density = LocalDensity.current

    var calendarHeightPx by remember { mutableIntStateOf(0) }
    var monthHeaderHeightPx by remember { mutableIntStateOf(0) }
    var weekdayHeaderHeightPx by remember { mutableIntStateOf(0) }
    var screenHeightPx by remember { mutableIntStateOf(0) }

    val p = viewModel.collapseProgress
    val headerHeightPx = monthHeaderHeightPx + weekdayHeaderHeightPx

    // 展开时网格高度 = 首次测量的日历总高度 - headers
    val expandedGridHeightPx = calendarHeightPx - headerHeightPx
    val weeksCount = 6

    // 折叠时网格高度公式（与 CalendarMonthPage 一致）：
    // gridH = rowH × (1 + (weeks-1) × (1-p))
    // 其中 rowH = expandedGridHeightPx / weeksCount
    val gridHeightPx = if (expandedGridHeightPx > 0 && p > 0f) {
        val rowH = expandedGridHeightPx.toFloat() / weeksCount
        (rowH * (1 + (weeksCount - 1) * (1f - p))).toInt()
    } else if (expandedGridHeightPx > 0) {
        expandedGridHeightPx
    } else 0

    val rowPaddingPx = with(density) { 4.dp.toPx() }.toInt()

    val cardTopPx = headerHeightPx + gridHeightPx + rowPaddingPx
    val cardHeightPx = screenHeightPx - cardTopPx

    val pagerModifier = if (p > 0.01f && expandedGridHeightPx > 0) {
        Modifier
            .height(with(density) { gridHeightPx.toDp() })
            .clipToBounds()
    } else {
        Modifier
    }

    if (p > 0f) {
        println("[View] p=$p monthH=$monthHeaderHeightPx weekdayH=$weekdayHeaderHeightPx expandedGridH=$expandedGridHeightPx gridH=$gridHeightPx cardTop=$cardTopPx cardH=$cardHeightPx screenH=$screenHeightPx calH=$calendarHeightPx isCollapsed=${viewModel.isCollapsed}")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .onSizeChanged { size ->
                screenHeightPx = size.height
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).onSizeChanged { size ->
                // 仅在展开时记录日历总高度（折叠时 HorizontalPager 不缩小）
                if (p < 0.01f) {
                    calendarHeightPx = size.height
                }
            }) {
            MonthHeader(
                year = currentYear,
                month = currentMonth,
                weekNumber = viewModel.getIsoWeekNumber(viewModel.selectedDate),
                modifier = Modifier.onSizeChanged { size ->
                    monthHeaderHeightPx = size.height
                }
            )
            WeekdayHeader(
                modifier = Modifier.fillMaxWidth().onSizeChanged { size ->
                    weekdayHeaderHeightPx = size.height
                }.padding(bottom = 4.dp)
            )
            // 完全折叠且无动画时显示 WeekPager，否则显示 CalendarPager（含下拉恢复过程）
            if (viewModel.isCollapsed && viewModel.collapseProgress >= 1f) {
                WeekPager(
                    selectedDate = viewModel.selectedDate,
                    today = today,
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onWeekChanged = { weekMonday ->
                        viewModel.selectDate(weekMonday)
                        currentYear = weekMonday.year
                        @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
                        currentMonth = weekMonday.monthNumber
                    }
                )
            } else {
                CalendarPager(
                    selectedDate = viewModel.selectedDate,
                    today = today,
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onMonthChanged = { year, month ->
                        viewModel.selectDate(LocalDate(year, month, 1))
                        currentYear = year
                        currentMonth = month
                    },
                    collapseProgress = viewModel.collapseProgress,
                    modifier = pagerModifier
                )
            }
        }
        if (cardHeightPx > 0) {
            BottomCard(
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(density) { cardHeightPx.toDp() })
                    .align(Alignment.BottomCenter)
            )
        }
    }
}