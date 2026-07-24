@file:OptIn(ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn
import kotlin.time.Instant
import plus.rua.project.CalendarViewModel
import plus.rua.project.ShiftKind
import plus.rua.project.composeTraceBeginSection
import plus.rua.project.composeTraceEndSection
import kotlin.math.abs
import kotlin.time.Clock
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import plus.rua.project.ShiftPatternStorage

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
    onNavigateToTools: () -> Unit = {},
    onNavigateToShiftSettings: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CalendarViewModel(
                    clock = Clock.System,
                    shiftStorage = ShiftPatternStorage.fromContext(context)
                )
            }
        }
    )

    // 设置页返回后 onResume 重读 storage,立即刷新班次
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshShiftPattern()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    val uiState by viewModel.uiState.collectAsState()
    val shiftPattern by viewModel.shiftPattern.collectAsState()
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
    var showDatePicker by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = START_PAGE, pageCount = { Int.MAX_VALUE })

    // 年视图分页器
    val yearPagerState = rememberPagerState(
        initialPage = START_PAGE,
        pageCount = { Int.MAX_VALUE }
    )

    // 视图切换时自动关闭菜单
    LaunchedEffect(isYearView) {
        isMenuExpanded = false
    }

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
        SharedTransitionLayout(
            modifier = Modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = isYearView,
                label = "month_year_transition",
                transitionSpec = {
                    fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                    ) togetherWith fadeOut(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                    )
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
                                onToday = onToday,
                                onYearMonthClick = { showDatePicker = true }
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
                            val shiftKindAt = remember(viewModel, shiftPattern) {
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
                                modifier = Modifier
                                    .clipToBounds()
                                    .sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "month_grid_${currentYear}_$currentMonth"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = { _, _ ->
                                            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                                        }
                                    )
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
                            userScrollEnabled = (this@AnimatedContent.transition.currentState == this@AnimatedContent.transition.targetState),
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
                                monthModifier = { month ->
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "month_grid_${pageYear}_$month"),
                                        animatedVisibilityScope = this@AnimatedContent,
                                        boundsTransform = { _, _ ->
                                            spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy)
                                        }
                                    )
                                },
                                modifier = Modifier
                            )
                        }
                    }
                    composeTraceEndSection()
                }
            }
        }
        BackHandler(enabled = isMenuExpanded) {
            isMenuExpanded = false
        }
        BackHandler(enabled = isYearView && !isMenuExpanded) {
            viewModel.toggleYearView()
        }

        // Scrim：半透明遮罩，在菜单展开时淡入拦截点击关闭菜单，增强渐变视觉与聚焦感
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) {
                        detectTapGestures { isMenuExpanded = false }
                    }
            )
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
                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f)) togetherWith
                        (fadeOut(tween(200)) + scaleOut(targetScale = 0.8f))
                },
                label = "fab_icon"
            ) { expanded ->
                val rotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioLowBouncy
                    ),
                    label = "fab_rotation"
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.Close else Icons.Filled.Menu,
                    contentDescription = if (expanded) "关闭菜单" else "打开菜单",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }
        }

        // 物理弹簧缩放动画菜单
        AnimatedVisibility(
            visible = isMenuExpanded,
            enter = scaleIn(
                initialScale = 0.2f,
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioLowBouncy
                ),
                transformOrigin = TransformOrigin(0f, 1f)
            ) + fadeIn(tween(180)),
            exit = scaleOut(
                targetScale = 0.2f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                transformOrigin = TransformOrigin(0f, 1f)
            ) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp + 56.dp + 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier
                        .width(170.dp)
                        .padding(vertical = 4.dp)
                ) {
                    MenuItem(
                        text = "月视图",
                        icon = Icons.Outlined.CalendarMonth,
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
                        icon = Icons.Outlined.DateRange,
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
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                    MenuItem(
                        text = "显示调休",
                        icon = Icons.Outlined.EventAvailable,
                        selected = showLegalHoliday,
                        onClick = {
                            isMenuExpanded = false
                            viewModel.toggleShowLegalHoliday()
                        }
                    )
                    MenuItem(
                        text = "班次设置",
                        icon = Icons.Outlined.Tune,
                        selected = false,
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToShiftSettings()
                        }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                    MenuItem(
                        text = "工具",
                        icon = Icons.Outlined.Build,
                        selected = false,
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToTools()
                        }
                    )
                    MenuItem(
                        text = "关于",
                        icon = Icons.Outlined.Info,
                        selected = false,
                        onClick = {
                            isMenuExpanded = false
                            onNavigateToAbout()
                        }
                    )
                }
            }
        }

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.toEpochMillis()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                viewModel.selectDate(millis.toLocalDate())
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("取消")
                    }
                }
            ) {
                DatePicker(state = datePickerState)
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
    val shiftPattern by viewModel.shiftPattern.collectAsState()
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
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 将 [LocalDate] 转换为 UTC 午夜的 epoch 毫秒。
 *
 * 用于 DatePicker 初始选中值，与 [Long.toLocalDate] 成对使用。
 */
private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/**
 * 将 epoch 毫秒转换为 UTC 日期的 [LocalDate]。
 *
 * DatePicker 返回选中日期的 UTC 午夜毫秒，经此函数得到本地逻辑日期。
 */
private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
