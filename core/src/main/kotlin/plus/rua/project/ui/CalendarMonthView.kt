package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import plus.rua.project.CalendarViewModel
import plus.rua.project.ShiftKind
import plus.rua.project.composeTraceBeginSection
import plus.rua.project.composeTraceEndSection
import kotlin.math.abs
import kotlin.time.Clock
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * 日历主界面，包含月/周视图切换、折叠动画和年视图转场。
 *
 * 折叠时日历从月视图收缩为周视图（1行），BottomCard 同步上移填充空间。
 * 通过左下角 FAB 菜单切换月/年视图。
 *
 * @param modifier 外部布局修饰符
 */
@Composable
fun CalendarMonthView(
    modifier: Modifier = Modifier,
    onNavigateToAbout: () -> Unit = {},
    onNavigateToTools: () -> Unit = {}
) {
    val viewModel = viewModel<CalendarViewModel>()
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val uiState by viewModel.uiState.collectAsState()
    val selectedDate = uiState.selectedDate
    val currentYear = selectedDate.year
    val currentMonth = selectedDate.month.number
    val isCollapsed = uiState.isCollapsed
    val isYearView = uiState.isYearView
    val yearViewYear = uiState.yearViewYear
    val collapseProgress = uiState.collapseProgress
    val showLegalHoliday = uiState.showLegalHoliday

    // 松手后 progress 从当前值 spring 动画到目标值（0 或 1）
    val animatedCollapseProgress by animateFloatAsState(
        targetValue = collapseProgress,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "collapseProgress"
    )

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    var rowHeightPx by remember { mutableIntStateOf(0) }
    var screenWidthPx by remember { mutableIntStateOf(0) }
    var isMenuExpanded by remember { mutableStateOf(false) }

    // 视图切换时自动关闭菜单
    LaunchedEffect(isYearView) {
        isMenuExpanded = false
    }

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    // 年视图分页器
    val yearPagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )

    // 年视图翻页时同步 yearViewYear（跟踪 settled page 差值）
    LaunchedEffect(yearPagerState) {
        var lastSettledPage = yearPagerState.currentPage
        snapshotFlow { yearPagerState.settledPage }.collect { page ->
            if (page != lastSettledPage) {
                val diff = page - lastSettledPage
                viewModel.setYearViewYear(viewModel.yearViewYear.value + diff)
                lastSettledPage = page
            }
        }
    }

    // 折叠态 WeekPager 切月时，持续同步 CalendarPager 的 pagerState
    LaunchedEffect(selectedDate) {
        val targetPage = yearMonthToPage(
            selectedDate.year, selectedDate.month.number,
            today.year, today.month.number
        )
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .semantics { testTagsAsResourceId = true }
            .onSizeChanged { size ->
                screenWidthPx = size.width
            }
    ) {
            AnimatedContent(
                targetState = isYearView,
                label = "month_year_transition",
                transitionSpec = {
                    val enter = scaleIn(
                        initialScale = 0.85f,
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(350, easing = LinearOutSlowInEasing))
                    val exit = scaleOut(
                        targetScale = 0.85f,
                        animationSpec = tween(350, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(350, easing = FastOutSlowInEasing))
                    enter togetherWith exit
                },
                modifier = Modifier.fillMaxSize()
            ) { yearViewActive ->
                if (!yearViewActive) {
                    composeTraceBeginSection("MonthView:Compose")
                    composeTraceBeginSection("CalendarPagerArea")
                    val layoutReady = rowHeightPx > 0
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .alpha(if (layoutReady) 1f else 0f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
                        ) {
                            val weekNumber = remember(selectedDate) {
                                viewModel.getIsoWeekNumber(selectedDate)
                            }
                            val onToday = remember(viewModel, today) {
                                { viewModel.selectDate(today) }
                            }
                            MonthHeader(
                                year = currentYear,
                                month = currentMonth,
                                weekNumber = weekNumber,
                                showToday = selectedDate != today,
                                onToday = onToday
                            )
                            WeekdayHeader(
                                modifier = Modifier.fillMaxWidth().padding(bottom = ROW_PADDING_DP.dp)
                            )
                            val onDateClick = remember(viewModel) {
                                { date: LocalDate -> viewModel.selectDate(date) }
                            }
                            val onMonthChanged = remember(viewModel, today) {
                                { year: Int, month: Int ->
                                    val date = if (year == today.year && today.month.number == month) today
                                    else LocalDate(year, Month(month), 1)
                                    viewModel.selectDate(date)
                                }
                            }
                            val shiftKindAt = remember(viewModel) {
                                { date: LocalDate -> viewModel.shiftKindAt(date) }
                            }
                            val onRowHeightMeasured = remember {
                                { h: Int -> if (h > 0) rowHeightPx = h }
                            }
                            CalendarPagerArea(
                                selectedDate = selectedDate,
                                today = today,
                                collapseProgress = animatedCollapseProgress,
                                showLegalHoliday = showLegalHoliday,
                                rowHeightPx = rowHeightPx,
                                screenWidthPx = screenWidthPx,
                                onDateClick = onDateClick,
                                onMonthChanged = onMonthChanged,
                                shiftKindAt = shiftKindAt,
                                onRowHeightMeasured = onRowHeightMeasured,
                                pagerState = pagerState,
                                modifier = Modifier.clipToBounds()
                            )
                            BottomCardArea(
                                viewModel = viewModel,
                                today = today,
                                rowHeightPx = rowHeightPx,
                                isYearView = isYearView,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    composeTraceEndSection()
                    composeTraceEndSection()
                } else {
                    composeTraceBeginSection("YearView:Compose")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
                    ) {
                        YearHeader(
                            year = yearViewYear,
                            currentYear = today.year,
                            onYearChange = { newYear ->
                                val offset = newYear - yearViewYear
                                val targetPage = yearPagerState.currentPage + offset
                                if (targetPage != yearPagerState.currentPage) {
                                    coroutineScope.launch { yearPagerState.animateScrollToPage(targetPage) }
                                }
                            }
                        )
                        HorizontalPager(
                            state = yearPagerState,
                            beyondViewportPageCount = 0,
                            flingBehavior = PagerDefaults.flingBehavior(state = yearPagerState),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) { page ->
                            // P0: 稳定 pageYear 计算，避免 settledPage/yearViewYear 不同步导致抖动
                            val pageYear = remember(page, yearViewYear, yearPagerState.settledPage) {
                                yearViewYear + (page - yearPagerState.settledPage)
                            }
                            YearGridView(
                                year = pageYear,
                                selectedMonth = if (pageYear == currentYear) currentMonth else 0,
                                today = today,
                                onMonthClick = { month ->
                                    composeTraceBeginSection("YearView:SelectMonth")
                                    viewModel.selectMonthFromYearView(month)
                                    val targetPage = yearMonthToPage(
                                        yearViewYear, month,
                                        today.year, today.month.number
                                    )
                                    if (targetPage != pagerState.currentPage) {
                                        coroutineScope.launch { pagerState.scrollToPage(targetPage) }
                                    }
                                    composeTraceEndSection()
                                },
                                modifier = Modifier
                            )
                        }
                    }
                    composeTraceEndSection()
                }
            }

        // FAB 浮动按钮
    FloatingActionButton(
            onClick = { isMenuExpanded = !isMenuExpanded },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp)
                .testTag("fab_menu"),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            AnimatedContent(
                targetState = isMenuExpanded,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "fab_icon"
            ) { expanded ->
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = if (expanded) "关闭菜单" else "打开菜单"
                )
            }
        }

        // Scrim：全透明，仅拦截点击关闭菜单，无动画
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = EnterTransition.None,
            exit = ExitTransition.None
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { isMenuExpanded = false }
                    }
            )
        }

        // 缩放动画菜单
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = scaleIn(
                initialScale = 0.2f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0f, 1f)
            ) + fadeIn(tween(150)),
            exit = scaleOut(
                targetScale = 0.2f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0f, 1f)
            ) + fadeOut(tween(100)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp + 56.dp + 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(modifier = Modifier.width(140.dp)) {
                    MenuItem(
                        text = "月视图",
                        selected = !isYearView,
                        onClick = {
                            isMenuExpanded = false
                            if (isYearView) {
                                composeTraceBeginSection("YearView→MonthView")
                                viewModel.toggleYearView()
                                composeTraceEndSection()
                            }
                        }
                    )
                    MenuItem(
                        text = "年视图",
                        selected = isYearView,
                        onClick = {
                            isMenuExpanded = false
                            if (!isYearView) {
                                composeTraceBeginSection("MonthView→YearView")
                                viewModel.toggleYearView()
                                composeTraceEndSection()
                            }
                        }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    MenuItem(
                        text = "显示调休",
                        selected = showLegalHoliday,
                        onClick = {
                            isMenuExpanded = false
                            viewModel.toggleShowLegalHoliday()
                        }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    MenuItem(
                        text = "工具",
                        selected = false,
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToTools()
                        }
                    )
                    MenuItem(
                        text = "关于",
                        selected = false,
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToAbout()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarPagerArea(
    selectedDate: LocalDate,
    today: LocalDate,
    collapseProgress: Float,
    showLegalHoliday: Boolean,
    rowHeightPx: Int,
    screenWidthPx: Int,
    onDateClick: (LocalDate) -> Unit,
    onMonthChanged: (year: Int, month: Int) -> Unit,
    shiftKindAt: (LocalDate) -> ShiftKind?,
    onRowHeightMeasured: ((Int) -> Unit)?,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val interpolatedWeeks by remember {
        derivedStateOf {
            val fraction = pagerState.currentPageOffsetFraction
            if (abs(fraction) > OFFSET_FRACTION_THRESHOLD) {
                val cp = pagerState.currentPage
                val baseWeeks = calculateWeeksCountForPage(cp, today)
                val targetPage = cp + if (fraction > 0) 1 else -1
                val targetWeeks = calculateWeeksCountForPage(targetPage, today)
                lerp(baseWeeks.toFloat(), targetWeeks.toFloat(), abs(fraction))
            } else {
                calculateWeeksCountForPage(pagerState.currentPage, today).toFloat()
            }
        }
    }

    val horizontalPaddingPx = remember { with(density) { (HORIZONTAL_PADDING_DP * 2).dp.toPx() } }
    val rowPadding2Px = remember { with(density) { (ROW_PADDING_DP * 2).dp.toPx() } }

    val estimatedRowHeightPx = if (screenWidthPx > 0) {
        val cellWidth = (screenWidthPx - horizontalPaddingPx) / 7
        (cellWidth + rowPadding2Px).toInt()
    } else 0

    val effectiveRowHeightPx = if (rowHeightPx > 0) rowHeightPx else estimatedRowHeightPx
    val effectiveWeeks = interpolatedWeeks

    val gridHeightPx = if (effectiveRowHeightPx > 0) {
        val rowH = effectiveRowHeightPx.toFloat()
        if (collapseProgress > OFFSET_FRACTION_THRESHOLD) {
            (rowH * (1 + (effectiveWeeks - 1) * (1f - collapseProgress))).toInt()
        } else {
            (rowH * effectiveWeeks).toInt()
        }
    } else 0

    val pagerModifier = if (rowHeightPx > 0 && gridHeightPx > 0) {
        Modifier
            .height(with(density) { gridHeightPx.toDp() })
            .then(modifier)
    } else {
        modifier
    }

    CalendarPager(
        selectedDate = selectedDate,
        today = today,
        onDateClick = onDateClick,
        onMonthChanged = onMonthChanged,
        collapseProgress = collapseProgress,
        rowHeightPx = rowHeightPx,
        effectiveWeeks = effectiveWeeks,
        shiftKindAt = shiftKindAt,
        showLegalHoliday = showLegalHoliday,
        onRowHeightMeasured = onRowHeightMeasured,
        pagerState = pagerState,
        modifier = pagerModifier
    )
}

@Composable
private fun BottomCardArea(
    viewModel: CalendarViewModel,
    today: LocalDate,
    rowHeightPx: Int,
    isYearView: Boolean,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dragRangeMinPx = remember { with(density) { DRAG_RANGE_MIN_DP.dp.toPx() } }
    val dragRangePx = if (rowHeightPx > 0) {
        maxOf(4f * rowHeightPx, dragRangeMinPx)
    } else {
        dragRangeMinPx
    }

    // 初始值固定为 1f（屏幕外），确保年→月切换时 BottomCard 能从屏幕外滑入
    val slideAnim = remember { Animatable(1f) }
    LaunchedEffect(isYearView) {
        slideAnim.animateTo(if (isYearView) 1f else 0f, tween(200))
    }
    val slideProgress = slideAnim.value
    // 延迟一帧显示 BottomCard，避免 AnimatedWebp 和 lunar 计算阻塞首帧
    var hasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(32)
        hasLoaded = true
    }
    val shouldShow = hasLoaded

    val uiState by viewModel.uiState.collectAsState()
    val shiftKind = viewModel.shiftKindAt(uiState.selectedDate)

    if (shouldShow) {
        BottomCard(
            isCollapsed = uiState.isCollapsed,
            selectedDate = uiState.selectedDate,
            today = today,
            shiftKind = shiftKind,
            onDrag = { delta ->
                composeTraceBeginSection("VM:collapseProgress:onDrag")
                viewModel.onDrag(delta)
                composeTraceEndSection()
            },
            onDragEnd = {
                composeTraceBeginSection("VM:collapseProgress:onDragEnd")
                viewModel.onDragEnd()
                composeTraceEndSection()
            },
            onExpandDrag = { delta ->
                composeTraceBeginSection("VM:collapseProgress:onExpandDrag")
                viewModel.onExpandDrag(delta)
                composeTraceEndSection()
            },
            onExpandDragEnd = {
                composeTraceBeginSection("VM:collapseProgress:onExpandDragEnd")
                viewModel.onExpandDragEnd()
                composeTraceEndSection()
            },
            dragRangePx = dragRangePx,
            modifier = modifier
                .offset(y = with(density) { (slideProgress * 300).dp })
                .alpha(1f - slideProgress)
        )
    }
}

@Composable
private fun MenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}
