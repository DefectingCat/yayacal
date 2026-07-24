package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ViewComfy
import androidx.compose.material.icons.outlined.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import plus.rua.project.DateRecord
import plus.rua.project.DateRecorderRepository
import plus.rua.project.DateRecorderViewModel
import plus.rua.project.RecordSortField
import plus.rua.project.RecordSortOrder

/** 视图模式：时光画廊、相册网格、紧凑矩阵。 */
enum class DateRecorderViewMode(
    val label: String,
    val icon: ImageVector
) {
    TIMELINE("时光流", Icons.Outlined.ViewStream),
    GRID("网格", Icons.Outlined.GridView),
    COMPACT("紧凑", Icons.Outlined.ViewComfy)
}

/** 排序菜单展示的字段选项。 */
private val sortFields = listOf(
    RecordSortField.SHOOT_DATE to "拍摄日期",
    RecordSortField.LINKED_DATE to "关联日期",
    RecordSortField.CREATED_AT to "创建时间"
)

/**
 * 日期记录器主界面，以沉浸式照片日记与时光画廊形式展示所有记录。
 *
 * 功能：
 * - 顶部控制栏：记录总数统计、三态视图模式切换（时光流 / 相册网格 / 紧凑矩阵）、排序菜单
 * - 时光流模式下按年月 Header 分组，沉浸展示卡片与详细关联信息
 * - 右下角 FloatingActionButton 拍照新建记录
 * - 支持长按与触控滑动多选、全选与批量删除
 *
 * @param onBack 返回回调
 * @param onOpenCamera 打开相机新建记录回调
 * @param onOpenRecord 打开指定记录详情回调，参数为记录 ID
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRecorderScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenRecord: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val viewModel: DateRecorderViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DateRecorderViewModel(DateRecorderRepository.fromContext(context)) }
        }
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val repo = remember { DateRecorderRepository.fromContext(context) }

    var viewMode by rememberSaveable { mutableStateOf(DateRecorderViewMode.GRID) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = if (uiState.selectionMode) {
                            "已选中 ${uiState.selectedIds.size} 项"
                        } else {
                            "日期记录器"
                        },
                        transitionSpec = {
                            (fadeIn(tween(200)) + slideInVertically { height -> height / 2 }) togetherWith
                                (fadeOut(tween(200)) + slideOutVertically { height -> -height / 2 })
                        },
                        label = "top_bar_title"
                    ) { titleText ->
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    AnimatedContent(
                        targetState = uiState.selectionMode,
                        transitionSpec = {
                            (fadeIn(tween(200)) + scaleIn(initialScale = 0.8f)) togetherWith
                                (fadeOut(tween(200)) + scaleOut(targetScale = 0.8f))
                        },
                        label = "top_bar_nav_icon"
                    ) { isSelectionMode ->
                        IconButton(onClick = {
                            if (isSelectionMode) viewModel.toggleSelectionMode()
                            else onBack()
                        }) {
                            Icon(
                                imageVector = if (isSelectionMode) Icons.Filled.Close else Icons.Filled.ChevronLeft,
                                contentDescription = if (isSelectionMode) "退出多选" else "返回"
                            )
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = uiState.selectionMode,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                        label = "top_bar_actions"
                    ) { isSelectionMode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelectionMode) {
                                IconButton(onClick = viewModel::toggleSelectAll) {
                                    Icon(Icons.Filled.Checklist, contentDescription = "全选")
                                }
                            } else if (!uiState.isLoading && uiState.records.isNotEmpty()) {
                                IconButton(onClick = viewModel::toggleSelectionMode) {
                                    Icon(Icons.Filled.Checklist, contentDescription = "多选")
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.selectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = viewModel::toggleSelectAll) {
                            Text(if (uiState.allSelected) "取消全选" else "全选")
                        }
                        Button(
                            onClick = { showBatchDeleteDialog = true },
                            enabled = uiState.selectedIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("删除 (${uiState.selectedIds.size})")
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.selectionMode,
                enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                FloatingActionButton(
                    onClick = onOpenCamera,
                    modifier = Modifier.testTag("date_recorder_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = "拍照记录")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isLoading && uiState.records.isNotEmpty()) {
                HeaderControlBar(
                    totalCount = uiState.records.size,
                    currentViewMode = viewMode,
                    sortOrder = uiState.sortOrder,
                    onViewModeChange = { viewMode = it },
                    onOpenSortMenu = { showSortMenu = true }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.records.isEmpty() -> {
                        EmptyState(onOpenCamera = onOpenCamera)
                    }
                    else -> {
                        RecordGrid(
                            records = uiState.records,
                            selectedIds = uiState.selectedIds,
                            selectionMode = uiState.selectionMode,
                            viewMode = viewMode,
                            photoRoot = repo,
                            onOpenRecord = onOpenRecord,
                            onToggleSelection = viewModel::toggleSelection,
                            onSetSelectedIds = viewModel::setSelectedIds
                        )
                    }
                }
            }
        }
    }

    if (showSortMenu) {
        SortDialog(
            currentSortOrder = uiState.sortOrder,
            onDismiss = { showSortMenu = false },
            onSelectSort = { newSortOrder ->
                viewModel.setSortOrder(newSortOrder)
                showSortMenu = false
            }
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除记录", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除选中的 ${uiState.selectedIds.size} 条日期记录吗？相关照片文件也将被一并清理，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDeleteDialog = false
                    viewModel.deleteSelected()
                }) {
                    Text("彻底删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/** 顶部的控制工具栏：统计数据、视图切换 segmented control、排序 Pill。 */
@Composable
private fun HeaderControlBar(
    totalCount: Int,
    currentViewMode: DateRecorderViewMode,
    sortOrder: RecordSortOrder,
    onViewModeChange: (DateRecorderViewMode) -> Unit,
    onOpenSortMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧：统计 Pill 与 排序入口
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalCount 篇记录",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                Surface(
                    onClick = onOpenSortMenu,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val fieldName = when (sortOrder.field) {
                            RecordSortField.SHOOT_DATE -> "拍摄日期"
                            RecordSortField.LINKED_DATE -> "关联日期"
                            RecordSortField.CREATED_AT -> "创建时间"
                        }
                        Icon(
                            imageVector = if (sortOrder.ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = fieldName,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // 右侧：视图切换按钮组 Segmented Control
            SegmentedViewModeSwitcher(
                currentViewMode = currentViewMode,
                onViewModeChange = onViewModeChange
            )
        }
    }
}

/** 视图模式分段切换器，带平滑弹簧滑动指示器与精准同心胶囊形状切角。 */
@Composable
private fun SegmentedViewModeSwitcher(
    currentViewMode: DateRecorderViewMode,
    onViewModeChange: (DateRecorderViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = DateRecorderViewMode.entries
    val selectedIndex = modes.indexOf(currentViewMode).coerceAtLeast(0)

    val itemWidth = 36.dp
    val itemHeight = 32.dp
    val outerPadding = 3.dp

    val indicatorOffset by animateDpAsState(
        targetValue = outerPadding + (itemWidth * selectedIndex),
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "mode_switcher_indicator_offset"
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = CircleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(outerPadding)
        ) {
            // 滑动指示器 (Sliding Indicator Background)
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = indicatorOffset.toPx() - outerPadding.toPx()
                    }
                    .size(width = itemWidth, height = itemHeight)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            // 图标按键（带柔和手感及颜色渐变）
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                modes.forEachIndexed { index, mode ->
                    val isSelected = index == selectedIndex
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(200),
                        label = "mode_switcher_icon_color"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "mode_switcher_icon_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(width = itemWidth, height = itemHeight)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onViewModeChange(mode) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = mode.icon,
                            contentDescription = mode.label,
                            tint = iconColor,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }
                }
            }
        }
    }
}

/** 排序方式选择对话框。 */
@Composable
private fun SortDialog(
    currentSortOrder: RecordSortOrder,
    onDismiss: () -> Unit,
    onSelectSort: (RecordSortOrder) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "排序方式",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 1. 排序字段选择
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "排序依据",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    sortFields.forEach { (field, label) ->
                        val isSelected = currentSortOrder.field == field
                        Surface(
                            onClick = {
                                onSelectSort(currentSortOrder.copy(field = field))
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (isSelected) {
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
                }

                // 2. 排序方向选择
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "排序顺序",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SortOrderDirectionOption(
                            text = "最新优先",
                            subtitle = "从新到旧",
                            icon = Icons.Filled.ArrowDownward,
                            isSelected = !currentSortOrder.ascending,
                            onClick = { onSelectSort(currentSortOrder.copy(ascending = false)) },
                            modifier = Modifier.weight(1f)
                        )
                        SortOrderDirectionOption(
                            text = "最早优先",
                            subtitle = "从旧到新",
                            icon = Icons.Filled.ArrowUpward,
                            isSelected = currentSortOrder.ascending,
                            onClick = { onSelectSort(currentSortOrder.copy(ascending = true)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("完成", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/** 排序方向单选 Chip 卡片。 */
@Composable
private fun SortOrderDirectionOption(
    text: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecordGrid(
    records: List<DateRecord>,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    viewMode: DateRecorderViewMode,
    photoRoot: DateRecorderRepository,
    onOpenRecord: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSetSelectedIds: (Set<Long>) -> Unit
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    val currentRecords by rememberUpdatedState(records)
    val currentSelectionMode by rememberUpdatedState(selectionMode)
    val currentSelectedIds by rememberUpdatedState(selectedIds)
    val currentOnSetSelectedIds by rememberUpdatedState(onSetSelectedIds)

    // 按年月（例如 "2026年07月"）分组
    val groupedRecords = remember(records) {
        records.groupBy { record ->
            val date = record.shootDate
            "${date.year}年${date.month.number.toString().padStart(2, '0')}月"
        }
    }

    SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = viewMode,
            label = "date_recorder_view_mode_transition",
            transitionSpec = {
                fadeIn(tween(280)) togetherWith fadeOut(tween(280))
            },
            modifier = Modifier.fillMaxSize()
        ) { currentMode ->
            val columns = when (currentMode) {
                DateRecorderViewMode.TIMELINE -> GridCells.Fixed(1)
                DateRecorderViewMode.GRID -> GridCells.Fixed(2)
                DateRecorderViewMode.COMPACT -> GridCells.Adaptive(minSize = 100.dp)
            }

            LazyVerticalGrid(
                state = gridState,
                columns = columns,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                            val startOffset = down.position
                            val startRecordId = findRecordIdAtOffset(gridState, startOffset) ?: return@awaitEachGesture
                            val startTime = down.uptimeMillis

                            var isDragSelecting = false
                            var isScrolling = false
                            val initialSelected = if (currentSelectionMode) currentSelectedIds else emptySet()

                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!pointerChange.pressed) break

                                val currentOffset = pointerChange.position
                                val delta = currentOffset - startOffset
                                val distance = delta.getDistance()
                                val elapsedTime = pointerChange.uptimeMillis - startTime

                                if (!isDragSelecting && !isScrolling) {
                                    if (currentSelectionMode) {
                                        if (distance > 20f && elapsedTime < 120L && kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 2f) {
                                            isScrolling = true
                                        } else if (distance >= 16f || (elapsedTime >= 120L && distance >= 6f)) {
                                            isDragSelecting = true
                                            currentOnSetSelectedIds(initialSelected + startRecordId)
                                        }
                                    } else {
                                        if (distance > 30f && elapsedTime < 200L && kotlin.math.abs(delta.y) > kotlin.math.abs(delta.x) * 1.5f) {
                                            isScrolling = true
                                        } else if (elapsedTime >= 250L || (elapsedTime >= 150L && distance >= 10f)) {
                                            isDragSelecting = true
                                            currentOnSetSelectedIds(setOf(startRecordId))
                                        }
                                    }
                                }

                                if (isDragSelecting) {
                                    pointerChange.consume()
                                    val currentRecordId = findRecordIdAtOffset(gridState, currentOffset)
                                    if (currentRecordId != null) {
                                        val startIdx = currentRecords.indexOfFirst { it.id == startRecordId }
                                        val currentIdx = currentRecords.indexOfFirst { it.id == currentRecordId }
                                        if (startIdx != -1 && currentIdx != -1) {
                                            val minIdx = minOf(startIdx, currentIdx)
                                            val maxIdx = maxOf(startIdx, currentIdx)
                                            val draggedIds = (minIdx..maxIdx).map { currentRecords[it].id }.toSet()
                                            currentOnSetSelectedIds(initialSelected + draggedIds)
                                        }
                                    }

                                    val viewportHeight = gridState.layoutInfo.viewportSize.height
                                    if (currentOffset.y < 120f) {
                                        coroutineScope.launch { gridState.scrollBy(-30f) }
                                    } else if (currentOffset.y > viewportHeight - 120f) {
                                        coroutineScope.launch { gridState.scrollBy(25f) }
                                    }
                                }
                            }
                        }
                    },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedRecords.forEach { (monthTitle, monthRecords) ->
                    item(
                        key = "header_$monthTitle",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        MonthHeaderItem(title = monthTitle, count = monthRecords.size)
                    }

                    items(
                        items = monthRecords,
                        key = { it.id }
                    ) { record ->
                        val photoUri = "file://${photoRoot.absoluteFileOf(record.photoPath).absolutePath}"
                        val isSelected = record.id in selectedIds

                        when (currentMode) {
                            DateRecorderViewMode.TIMELINE -> {
                                TimelineRecordCard(
                                    record = record,
                                    photoUri = photoUri,
                                    isSelected = isSelected,
                                    selectionMode = selectionMode,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onClick = {
                                        if (selectionMode) onToggleSelection(record.id)
                                        else onOpenRecord(record.id)
                                    }
                                )
                            }
                            DateRecorderViewMode.GRID -> {
                                GridRecordCard(
                                    record = record,
                                    photoUri = photoUri,
                                    isSelected = isSelected,
                                    selectionMode = selectionMode,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onClick = {
                                        if (selectionMode) onToggleSelection(record.id)
                                        else onOpenRecord(record.id)
                                    }
                                )
                            }
                            DateRecorderViewMode.COMPACT -> {
                                CompactRecordCard(
                                    record = record,
                                    photoUri = photoUri,
                                    isSelected = isSelected,
                                    selectionMode = selectionMode,
                                    animatedVisibilityScope = this@AnimatedContent,
                                    onClick = {
                                        if (selectionMode) onToggleSelection(record.id)
                                        else onOpenRecord(record.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 寻找触控位置命中的 DateRecord ID。 */
private fun findRecordIdAtOffset(gridState: LazyGridState, offset: Offset): Long? {
    val itemsInfo = gridState.layoutInfo.visibleItemsInfo
    if (itemsInfo.isEmpty()) return null

    val hit = itemsInfo.firstOrNull { item ->
        val x = item.offset.x
        val y = item.offset.y
        offset.x >= x && offset.x <= x + item.size.width &&
            offset.y >= y && offset.y <= y + item.size.height
    }
    return hit?.key as? Long
}

/** 按月分组的时光 Header。 */
@Composable
private fun MonthHeaderItem(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "· $count 条记录",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 时光流模式 (TIMELINE) 卡片：宽幅展图、标题手记摘要、拍摄与关联日期 Badge。 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
private fun SharedTransitionScope.TimelineRecordCard(
    record: DateRecord,
    photoUri: String,
    isSelected: Boolean,
    selectionMode: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardScale by animateFloatAsState(
        targetValue = if (selectionMode && isSelected) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "timeline_card_scale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selectionMode && isSelected) 2.5.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "timeline_card_border"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(borderWidth, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key = "card_${record.id}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box {
                AsyncImage(
                    uri = photoUri,
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "photo_${record.id}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                )

                // 顶端多选 Badge
                androidx.compose.animation.AnimatedVisibility(
                    visible = selectionMode,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    SelectionBadge(isSelected = isSelected)
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = record.title.ifBlank { "无标题记录" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (record.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 日期标签行
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 拍摄日期 Pill
                    DateTag(
                        icon = Icons.Outlined.PhotoCamera,
                        text = "拍摄 ${record.shootDate}",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 关联日期 Pill
                    if (record.linkedDate != null) {
                        val relativeDays = computeRelativeDaysDescription(record.shootDate, record.linkedDate)
                        DateTag(
                            icon = Icons.Outlined.CalendarToday,
                            text = "关联 ${record.linkedDate}" + if (relativeDays.isNotBlank()) " ($relativeDays)" else "",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/** 相册网格模式 (GRID) 卡片：双列拍立得相框与沉浸阴影。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.GridRecordCard(
    record: DateRecord,
    photoUri: String,
    isSelected: Boolean,
    selectionMode: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardScale by animateFloatAsState(
        targetValue = if (selectionMode && isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "grid_card_scale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selectionMode && isSelected) 2.5.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "grid_card_border"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(borderWidth, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key = "card_${record.id}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
    ) {
        Box {
            AsyncImage(
                uri = photoUri,
                contentDescription = record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "photo_${record.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            )

            // 底部蒙层与文字信息
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = record.title.ifBlank { "记录" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${record.shootDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    if (record.linkedDate != null) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = selectionMode,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                SelectionBadge(isSelected = isSelected)
            }
        }
    }
}

/** 紧凑矩阵模式 (COMPACT) 卡片：高效高密度。 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.CompactRecordCard(
    record: DateRecord,
    photoUri: String,
    isSelected: Boolean,
    selectionMode: Boolean,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardScale by animateFloatAsState(
        targetValue = if (selectionMode && isSelected) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "compact_card_scale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selectionMode && isSelected) 2.dp else 0.dp,
        label = "compact_card_border"
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(borderWidth, MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key = "card_${record.id}"),
                animatedVisibilityScope = animatedVisibilityScope
            )
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
    ) {
        Box {
            AsyncImage(
                uri = photoUri,
                contentDescription = record.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "photo_${record.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = selectionMode,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            ) {
                SelectionBadge(isSelected = isSelected)
            }
        }
    }
}

/** 日期标签 Chip。 */
@Composable
private fun DateTag(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** 计算拍摄日期与关联日期之间的天数关联描述。 */
private fun computeRelativeDaysDescription(shootDate: LocalDate, linkedDate: LocalDate): String {
    val diff = linkedDate.toEpochDays() - shootDate.toEpochDays()
    return when {
        diff == 0L -> "当日"
        diff > 0L -> "+${diff}天"
        else -> "${diff}天"
    }
}

/** 选择标识角标。 */
@Composable
private fun SelectionBadge(isSelected: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
        animationSpec = tween(200),
        label = "selection_badge_bg"
    )
    val badgeScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "selection_badge_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = badgeScale
                scaleY = badgeScale
            }
            .clip(CircleShape)
            .background(bg)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visible = isSelected,
                enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "已选中",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** 全新升级的空状态设计。 */
@Composable
private fun EmptyState(onOpenCamera: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "还没有时光记录",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "拍摄照片留下美好瞬间，并将其固定在特定的日历日子上，打造属于你的相册日记。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onOpenCamera,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("立即拍摄第一条记录")
        }
    }
}
