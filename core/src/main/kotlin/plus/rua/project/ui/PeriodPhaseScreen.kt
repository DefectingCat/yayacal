package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import plus.rua.project.CyclePhase
import plus.rua.project.PeriodDayInfo
import plus.rua.project.PeriodTimeline
import plus.rua.project.PeriodUiState
import plus.rua.project.PeriodViewModel
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 今日周期页（有状态版本），页面回到前台时刷新「今天」并同步。
 *
 * @param onBack 点击左上角返回时触发
 * @param viewModel 经期页面共用的 ViewModel，默认按应用 Context 创建
 * @param modifier 布局修饰符
 */
@Composable
fun PeriodPhaseScreen(
    onBack: () -> Unit,
    viewModel: PeriodViewModel = periodViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    PeriodPhaseScreen(uiState = uiState, onBack = onBack, modifier = modifier)
}

/**
 * 今日周期页（无状态版本）：顶部日期条与下方卡片联动，左右滑动查看每天处在周期的哪个阶段。
 * 打开时停在今天。
 *
 * @param uiState 经期界面状态
 * @param onBack 点击左上角返回时触发
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPhaseScreen(
    uiState: PeriodUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = uiState.today
    val timeline =
        remember(uiState.document, uiState.forecast, today) {
            PeriodTimeline.build(uiState.document, uiState.forecast, today)
        }
    val dates = timeline.dates
    val todayPage = dates.indexOf(today).coerceAtLeast(0)
    val palette = periodPalette()
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 起始日期变化（同步到新记录或跨天）后页码对应的日期会变，重新停在今天
    key(timeline.firstDate) {
        val pagerState = rememberPagerState(initialPage = todayPage) { dates.size }
        Box(
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .semantics { testTagsAsResourceId = true },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Brush.verticalGradient(listOf(palette.period.copy(alpha = 0.22f), Color.Transparent))),
            )
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("今日周期", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, contentDescription = "返回") }
                        },
                        actions = {
                            if (pagerState.currentPage != todayPage) {
                                TextButton(
                                    onClick = { scope.launch { pagerState.animateScrollToPage(todayPage) } },
                                    modifier = Modifier.testTag("period_phase_today"),
                                ) { Text("回到今天") }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
                containerColor = Color.Transparent,
                // 透明背景推不出内容色，显式指定，否则暗色主题下日期条文字是黑色
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) { innerPadding ->
                Column(Modifier.fillMaxSize().padding(innerPadding)) {
                    PhaseDateStrip(
                        dates = dates,
                        today = today,
                        pagerState = pagerState,
                        onSelect = { page -> scope.launch { pagerState.animateScrollToPage(page) } },
                    )
                    HorizontalPager(
                        state = pagerState,
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        pageSpacing = 12.dp,
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("period_phase_pager"),
                    ) { page ->
                        val date = dates[page]
                        val info = remember(timeline, date) { timeline.dayInfo(date) }
                        Column(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PhaseDayCard(info = info, today = today, onShowGuide = { showGuide = true })
                            Text(
                                text = "阶段由记录和预测推算，仅供参考，不能作为避孕或医疗依据。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showGuide) {
        PhaseGuideSheet(lutealLength = uiState.document.settings.lutealLength, onDismiss = { showGuide = false })
    }
}

/** 日期条上的文字：今天写「今天」，其余写「10/8 周四」。 */
private fun LocalDate.stripLabel(today: LocalDate): String = if (this == today) "今天" else "${month.number}/$day ${weekdayLabel()}"

/**
 * 与卡片联动的日期条：一屏三个日期，按 pager 的滑动进度平移，离中间越远越淡。
 * 偏移和透明度在布局、绘制阶段读取滑动进度，滑动时不触发重组。
 */
@Composable
private fun PhaseDateStrip(
    dates: List<LocalDate>,
    today: LocalDate,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val current = pagerState.currentPage
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp).clipToBounds()) {
        val itemWidth = maxWidth / 3
        val itemWidthPx = with(LocalDensity.current) { itemWidth.toPx() }
        for (page in (current - 2).coerceAtLeast(0)..(current + 2).coerceAtMost(dates.lastIndex)) {
            key(page) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                    Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .offset {
                            val distance = page - pagerState.currentPage - pagerState.currentPageOffsetFraction
                            IntOffset(((distance + 1) * itemWidthPx).roundToInt(), 0)
                        }.graphicsLayer {
                            val distance = abs(page - pagerState.currentPage - pagerState.currentPageOffsetFraction)
                            alpha = (1f - distance * 0.55f).coerceIn(0.25f, 1f)
                            val scale = 1f - distance.coerceAtMost(1f) * 0.12f
                            scaleX = scale
                            scaleY = scale
                        }.clip(CircleShape)
                        .clickable { onSelect(page) },
                ) {
                    Text(
                        text = dates[page].stripLabel(today),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (page == current) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** 卡片标题与副标题。 */
private fun phaseTexts(
    info: PeriodDayInfo,
    today: LocalDate,
): Pair<String, String> = when (info) {
    is PeriodDayInfo.NoRecord -> {
        "还没有记录" to "记录一次经期后，这里会显示每天处在周期的哪个阶段"
    }

    is PeriodDayInfo.Late -> {
        val expected = info.cycle.nextStart.periodLabel(today.year)
        if (info.days == 0) {
            "预计经期开始" to "这天没有记录到经期"
        } else {
            "经期推迟 · 第${info.days}天" to "原预计 $expected 开始，来了记得在首页记录"
        }
    }

    is PeriodDayInfo.InPhase -> {
        val cycle = info.cycle
        val title = if (info.phase == CyclePhase.OVULATION) "排卵日" else "${info.phase.label} · 第${info.phaseDay}天"
        val subtitle =
            when {
                info.phase != CyclePhase.MENSTRUAL -> {
                    "距离下次经期（${cycle.nextStart.periodLabel(today.year)}）还有 ${info.daysUntilNext} 天"
                }

                cycle.periodEndKnown -> {
                    "这次经期 ${cycle.start.periodLabel(today.year)} – ${cycle.periodEnd.periodLabel(today.year)}，共 ${cycle.periodDays} 天"
                }

                else -> {
                    "预计 ${cycle.periodEnd.periodLabel(today.year)} 结束"
                }
            }
        title to subtitle
    }
}

@Composable
private fun PhaseDayCard(
    info: PeriodDayInfo,
    today: LocalDate,
    onShowGuide: () -> Unit,
) {
    val palette = periodPalette()
    val (title, subtitle) = phaseTexts(info, today)
    val titleColor =
        when (info) {
            is PeriodDayInfo.InPhase -> palette.phase(info.phase)
            is PeriodDayInfo.Late -> palette.period
            is PeriodDayInfo.NoRecord -> MaterialTheme.colorScheme.onSurface
        }
    Card(
        shape = PeriodCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().testTag("period_phase_card"),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.testTag("period_phase_title"),
                )
                if (info !is PeriodDayInfo.NoRecord) {
                    IconButton(onClick = onShowGuide, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = "阶段说明",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (info.date > today) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "预测",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            PeriodPhaseRing(info = info, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp))
        }
    }
}

private fun phaseDescription(
    phase: CyclePhase,
    lutealLength: Int,
): String = when (phase) {
    CyclePhase.MENSTRUAL -> "经期开始到结束，通常持续 3–7 天。注意保暖，多休息。"
    CyclePhase.FOLLICULAR -> "经期结束到排卵前。不少人这段时间精力和心情都比较好。"
    CyclePhase.OVULATION -> "由下次经期开始日往前推 $lutealLength 天（设置里的黄体期天数）估算，不是实际测得。"
    CyclePhase.LUTEAL -> "排卵后到下次经期前。临近经期时可能出现胸胀、情绪起伏等经前反应。"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhaseGuideSheet(
    lutealLength: Int,
    onDismiss: () -> Unit,
) {
    val palette = periodPalette()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("周期的四个阶段", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            CyclePhase.entries.forEach { phase ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette.phase(phase)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(phase.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            phaseDescription(phase, lutealLength),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "阶段由记录和预测推算，仅供参考，不能作为避孕或医疗依据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
