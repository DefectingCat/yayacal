package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import plus.rua.project.CalendarViewModel
import plus.rua.project.composeTraceBeginSection
import plus.rua.project.composeTraceEndSection
import kotlin.math.abs
import kotlin.time.Clock

// region 性能监控工具
private fun uiPerfLog(tag: String, msg: String) {
    println("[UI-PERF:${Thread.currentThread().name}] $tag | $msg")
}
// endregion

/**
 * 日历主界面，包含月/周视图切换、折叠动画和年视图共享元素转场。
 *
 * 折叠时日历从月视图收缩为周视图（1行），BottomCard 同步上移填充空间。
 * 通过左下角 FAB 菜单切换月/年视图，使用 SharedTransitionLayout 实现共享元素转场。
 *
 * @param modifier 外部布局修饰符
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CalendarMonthView(
    modifier: Modifier = Modifier,
    onNavigateToAbout: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { CalendarViewModel(coroutineScope) }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }
    val currentYear by remember { derivedStateOf { viewModel.selectedDate.year } }

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val currentMonth by remember { derivedStateOf { viewModel.selectedDate.month.number } }
    val density = LocalDensity.current

    var rowHeightPx by remember { mutableIntStateOf(0) }
    var screenWidthPx by remember { mutableIntStateOf(0) }
    var isMenuExpanded by remember { mutableStateOf(false) }
    // 视图切换时自动关闭菜单
    LaunchedEffect(viewModel.isYearView) {
        isMenuExpanded = false
    }

    // 重组计数器
    var recomposeCount by remember { mutableIntStateOf(0) }
    recomposeCount++

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    // 年视图分页器
    val yearPagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )

    // 进入年视图时同步 yearPagerState 到当前年
    LaunchedEffect(viewModel.isYearView) {
        if (viewModel.isYearView) {
            if (yearPagerState.currentPage != START_PAGE) {
                yearPagerState.scrollToPage(START_PAGE)
            }
        }
    }

    // 年视图翻页时同步 yearViewYear
    LaunchedEffect(yearPagerState) {
        snapshotFlow { yearPagerState.settledPage }.collect { page ->
            val offset = page - START_PAGE
            val targetYear = viewModel.selectedDate.year + offset
            if (targetYear != viewModel.yearViewYear) {
                viewModel.yearViewYear = targetYear
            }
        }
    }

    // 折叠态 WeekPager 切月时，持续同步 CalendarPager 的 pagerState
    LaunchedEffect(viewModel.selectedDate) {
        @Suppress("DEPRECATION") // monthNumber 无替代 API
        val targetPage = yearMonthToPage(
            viewModel.selectedDate.year, viewModel.selectedDate.month.number,
            today.year, today.month.number
        )
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    uiPerfLog("CalendarMonthView", "composition #$recomposeCount isYearView=${viewModel.isYearView}")
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .onSizeChanged { size ->
                screenWidthPx = size.width
            }
    ) {
        SharedTransitionLayout {
            val sharedScope = this
            AnimatedContent(
                targetState = viewModel.isYearView,
                label = "month_year_transition",
                transitionSpec = {
                    uiPerfLog("CalendarMonthView", "AnimatedContent transitionSpec target=$targetState initial=$initialState")
                    val enter = fadeIn(tween(300, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 6 }
                    val exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                        slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { -it / 6 }
                    enter togetherWith exit
                },
                modifier = Modifier.fillMaxSize()
            ) { isYearView ->
                val contentStart = System.nanoTime()
                uiPerfLog("CalendarMonthView", "AnimatedContent content lambda START isYearView=$isYearView")
                if (!isYearView) {
                    composeTraceBeginSection("MonthView:Compose")
                    val layoutReady = rowHeightPx > 0
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(if (layoutReady) 1f else 0f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
                        ) {
                            MonthHeader(
                                year = currentYear,
                                month = currentMonth,
                                weekNumber = viewModel.getIsoWeekNumber(viewModel.selectedDate),
                                showToday = viewModel.selectedDate != today,
                                onToday = {
                                    viewModel.selectDate(today)
                                }
                            )
                            WeekdayHeader(
                                modifier = Modifier.fillMaxWidth().padding(bottom = ROW_PADDING_DP.dp)
                            )
                            with(sharedScope) {
                                CalendarPagerArea(
                                    viewModel = viewModel,
                                    today = today,
                                    rowHeightPx = rowHeightPx,
                                    screenWidthPx = screenWidthPx,
                                    onRowHeightMeasured = { h ->
                                        if (h > 0) rowHeightPx = h
                                    },
                                    pagerState = pagerState,
                                    modifier = Modifier
                                        .sharedElement(
                                            sharedContentState = rememberSharedContentState(
                                                key = "month_grid_${currentYear}_${currentMonth}"
                                            ),
                                            animatedVisibilityScope = this@AnimatedContent,
                                            boundsTransform = { _, _ ->
                                                tween(400, easing = FastOutSlowInEasing)
                                            }
                                        )
                                        .clipToBounds()
                                )
                            }
                            BottomCardArea(
                                viewModel = viewModel,
                                today = today,
                                rowHeightPx = rowHeightPx,
                                isYearView = viewModel.isYearView,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    val monthViewMs = (System.nanoTime() - contentStart) / 1_000_000.0
                    uiPerfLog("CalendarMonthView", "MonthView content lambda END elapsed=${"%.3f".format(monthViewMs)}ms")
                    composeTraceEndSection()
                } else {
                    composeTraceBeginSection("YearView:Compose")
                    val yearViewStart = System.nanoTime()
                    uiPerfLog("CalendarMonthView", "YearView:Compose START")
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = HORIZONTAL_PADDING_DP.dp)
                    ) {
                        YearHeader(
                            year = viewModel.yearViewYear,
                            currentYear = today.year,
                            onYearChange = { newYear ->
                                val offset = newYear - viewModel.yearViewYear
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
                            val pageStart = System.nanoTime()
                            val pageOffset = abs(yearPagerState.currentPageOffsetFraction)
                            val isCurrentPage = page == yearPagerState.currentPage
                            val crossFadeAlpha = if (isCurrentPage) {
                                1f - pageOffset
                            } else {
                                pageOffset
                            }
                            val pageYear = viewModel.selectedDate.year + (page - START_PAGE)
                            uiPerfLog("CalendarMonthView", "YearPager page=$page pageYear=$pageYear crossFadeAlpha=${"%.2f".format(crossFadeAlpha)} isCurrentPage=$isCurrentPage offset=${"%.3f".format(pageOffset)}")
                            YearGridView(
                                year = pageYear,
                                selectedMonth = if (pageYear == currentYear) currentMonth else 0,
                                today = today,
                                onMonthClick = { month ->
                                    viewModel.selectMonthFromYearView(month)
                                    @Suppress("DEPRECATION") // monthNumber 无替代 API
                                    val targetPage = yearMonthToPage(
                                        viewModel.yearViewYear, month,
                                        today.year, today.month.number
                                    )
                                    if (targetPage != pagerState.currentPage) {
                                        coroutineScope.launch { pagerState.scrollToPage(targetPage) }
                                    }
                                },
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = this@AnimatedContent,
                                modifier = Modifier.alpha(crossFadeAlpha)
                            )
                        }
                    }
                    val yearViewMs = (System.nanoTime() - yearViewStart) / 1_000_000.0
                    uiPerfLog("CalendarMonthView", "YearView:Compose END elapsed=${"%.3f".format(yearViewMs)}ms")
                    composeTraceEndSection()
                }
                val contentTotalMs = (System.nanoTime() - contentStart) / 1_000_000.0
                uiPerfLog("CalendarMonthView", "AnimatedContent content lambda END total=${"%.3f".format(contentTotalMs)}ms")
            }
        }

        // FAB 浮动按钮
    FloatingActionButton(
            onClick = { isMenuExpanded = !isMenuExpanded },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
            MenuIcon(color = iconColor)
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
                        selected = !viewModel.isYearView,
                        onClick = {
                            isMenuExpanded = false
                            if (viewModel.isYearView) viewModel.toggleYearView()
                        }
                    )
                    MenuItem(
                        text = "年视图",
                        selected = viewModel.isYearView,
                        onClick = {
                            isMenuExpanded = false
                            if (!viewModel.isYearView) viewModel.toggleYearView()
                        }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
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
private fun MenuIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        val lineSpacing = 4.dp.toPx()
        val totalHeight = strokeWidth * 3 + lineSpacing * 2
        val startY = (size.height - totalHeight) / 2
        repeat(3) { i ->
            drawLine(
                color = color,
                start = Offset(0f, startY + i * (strokeWidth + lineSpacing)),
                end = Offset(size.width, startY + i * (strokeWidth + lineSpacing)),
                strokeWidth = strokeWidth
            )
        }
    }
}

@Composable
private fun CalendarPagerArea(
    viewModel: CalendarViewModel,
    today: LocalDate,
    rowHeightPx: Int,
    screenWidthPx: Int,
    onRowHeightMeasured: ((Int) -> Unit)?,
    pagerState: PagerState,
    modifier: Modifier = Modifier
) {
    val areaStart = System.nanoTime()
    var areaRecomposeCount by remember { mutableIntStateOf(0) }
    areaRecomposeCount++
    uiPerfLog("CalendarPagerArea", "composition #$areaRecomposeCount collapseProgress=${viewModel.collapseProgress} page=${pagerState.currentPage} rowHeightPx=$rowHeightPx")
    val density = LocalDensity.current
    val collapseProgress = viewModel.collapseProgress

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

    if (viewModel.isCollapsed && collapseProgress >= 1f) {
        WeekPager(
            selectedDate = viewModel.selectedDate,
            today = today,
            onDateClick = { date -> viewModel.selectDate(date) },
            onWeekChanged = { weekMonday ->
                val weekSunday = weekMonday.plus(DatePeriod(days = 6))
                val date = when {
                    today in weekMonday..weekSunday -> today
                    weekMonday.month != weekSunday.month -> {
                        if (weekMonday < viewModel.selectedDate) {
                            @Suppress("DEPRECATION") // monthNumber 无替代 API
                            LocalDate(weekSunday.year, weekSunday.month.number, 1)
                        } else {
                            weekMonday
                        }
                    }

                    else -> weekMonday
                }
                viewModel.selectDate(date)
            },
            shiftKindAt = { date -> viewModel.shiftKindAt(date) },
            showLegalHoliday = viewModel.showLegalHoliday,
            modifier = pagerModifier
        )
    } else {
        CalendarPager(
            selectedDate = viewModel.selectedDate,
            today = today,
            onDateClick = { date -> viewModel.selectDate(date) },
            onMonthChanged = { year, month ->
                @Suppress("DEPRECATION") // monthNumber 无替代 API
                val date =
                    if (year == today.year && today.month.number == month) today
                    else LocalDate(year, month, 1)
                viewModel.selectDate(date)
            },
            collapseProgress = collapseProgress,
            rowHeightPx = rowHeightPx,
            effectiveWeeks = effectiveWeeks,
            shiftKindAt = { date -> viewModel.shiftKindAt(date) },
            showLegalHoliday = viewModel.showLegalHoliday,
            onRowHeightMeasured = onRowHeightMeasured,
            pagerState = pagerState,
            modifier = pagerModifier
        )
    }
    val areaTotalMs = (System.nanoTime() - areaStart) / 1_000_000.0
    uiPerfLog("CalendarPagerArea", "END elapsed=${"%.3f".format(areaTotalMs)}ms")
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

    val slideProgress by animateFloatAsState(
        targetValue = if (isYearView) 1f else 0f,
        animationSpec = tween(350, delayMillis = 100, easing = FastOutSlowInEasing),
        label = "bottomCardSlide"
    )

    BottomCard(
        viewModel = viewModel,
        selectedDate = viewModel.selectedDate,
        today = today,
        dragRangePx = dragRangePx,
        modifier = modifier.graphicsLayer {
            translationY = slideProgress * 200.dp.toPx()
            alpha = 1f - slideProgress
        }
    )
}

@Composable
private fun MenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (selected) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
