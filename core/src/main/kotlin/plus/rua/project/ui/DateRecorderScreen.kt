package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.panpf.sketch.AsyncImage
import plus.rua.project.DateRecorderRepository
import plus.rua.project.DateRecorderViewModel
import plus.rua.project.DateRecord
import plus.rua.project.RecordSortField
import plus.rua.project.RecordSortOrder

/**
 * 日期记录器主界面，以相册形式展示所有记录。
 *
 * 功能：
 * - 右下角浮动按钮拍摄新照片创建记录
 * - 点击单条记录进入详情页
 * - TopAppBar 右侧"排序"菜单：3 个排序字段，点击已选字段切换升降序
 * - TopAppBar 右侧"多选"按钮进入多选态，可批量删除
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

    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = if (uiState.selectionMode) {
                            "已选 ${uiState.selectedIds.size}"
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
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
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
                                imageVector = if (isSelectionMode) Icons.Filled.Close
                                else Icons.Filled.ChevronLeft,
                                contentDescription = if (isSelectionMode) "退出多选" else "返回"
                            )
                        }
                    }
                },
                actions = {
                    AnimatedContent(
                        targetState = uiState.selectionMode,
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        },
                        label = "top_bar_actions"
                    ) { isSelectionMode ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelectionMode) {
                                IconButton(onClick = viewModel::toggleSelectAll) {
                                    Icon(Icons.Filled.Checklist, contentDescription = "全选")
                                }
                            } else if (!uiState.isLoading && uiState.records.isNotEmpty()) {
                                // 排序菜单
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(Icons.Filled.Sort, contentDescription = "排序")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        sortFields.forEach { (field, label) ->
                                            val selected = uiState.sortOrder.field == field
                                            DropdownMenuItem(
                                                text = { Text(label) },
                                                onClick = {
                                                    viewModel.setSortOrder(
                                                        RecordSortOrder.nextAfter(uiState.sortOrder, field)
                                                    )
                                                    showSortMenu = false
                                                },
                                                leadingIcon = if (selected) {
                                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                                } else {
                                                    null
                                                },
                                                trailingIcon = if (selected) {
                                                    {
                                                        Icon(
                                                            imageVector = if (uiState.sortOrder.ascending) {
                                                                Icons.Filled.ArrowUpward
                                                            } else {
                                                                Icons.Filled.ArrowDownward
                                                            },
                                                            contentDescription = if (uiState.sortOrder.ascending) {
                                                                "旧→新"
                                                            } else {
                                                                "新→旧"
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    null
                                                }
                                            )
                                        }
                                    }
                                }
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
                BottomAppBar {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = viewModel::toggleSelectAll) {
                            Text(if (uiState.allSelected) "取消全选" else "全选")
                        }
                        TextButton(
                            onClick = { showBatchDeleteDialog = true },
                            enabled = uiState.selectedIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "删除(${uiState.selectedIds.size})",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(start = 8.dp)
                            )
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
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = "拍照记录")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                uiState.records.isEmpty() -> EmptyState()
                else -> RecordGrid(
                    records = uiState.records,
                    selectedIds = uiState.selectedIds,
                    selectionMode = uiState.selectionMode,
                    photoRoot = repo,
                    onOpenRecord = onOpenRecord,
                    onToggleSelection = viewModel::toggleSelection,
                    onStartSelectionWith = viewModel::startSelectionModeWith,
                    onSetSelectedIds = viewModel::setSelectedIds
                )
            }
        }
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除记录") },
            text = { Text("确定删除选中的 ${uiState.selectedIds.size} 条记录吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showBatchDeleteDialog = false
                    viewModel.deleteSelected()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

/** 排序菜单展示的字段选项，点击已选字段时切换方向（见 [RecordSortOrder.nextAfter]）。 */
private val sortFields = listOf(
    RecordSortField.SHOOT_DATE to "拍摄日期",
    RecordSortField.LINKED_DATE to "关联日期",
    RecordSortField.CREATED_AT to "创建时间"
)

@Composable
private fun RecordGrid(
    records: List<DateRecord>,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    photoRoot: DateRecorderRepository,
    onOpenRecord: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onStartSelectionWith: (Long) -> Unit,
    onSetSelectedIds: (Set<Long>) -> Unit
) {
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    var initialDragIndex by remember { mutableStateOf<Int?>(null) }
    var initialSelectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(records, selectionMode, selectedIds) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Initial, requireUnconsumed = false)
                    val startOffset = down.position
                    val startIndex = findItemIndexAtOffset(gridState, startOffset) ?: return@awaitEachGesture

                    var isDragSelecting = false
                    val initialSelected = if (selectionMode) selectedIds else emptySet()

                    if (!selectionMode) {
                        // 普通模式下：等待 300ms 长按判定
                        val longPressResult = withTimeoutOrNull(300L) {
                            while (true) {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) return@withTimeoutOrNull false
                                if ((change.position - startOffset).getDistance() > 20f) {
                                    // 移动距离大于 20px 判定为滚屏而非长按
                                    return@withTimeoutOrNull false
                                }
                            }
                            true
                        }
                        if (longPressResult == null) {
                            // 300ms 超时到达且未松手/未远移，触发长按多选
                            isDragSelecting = true
                            val startId = records[startIndex].id
                            onSetSelectedIds(setOf(startId))
                        }
                    }

                    // 持续监听 Pointer 移动手势
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val pointerChange = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointerChange.pressed) break

                        val currentOffset = pointerChange.position
                        val distance = (currentOffset - startOffset).getDistance()

                        if (!isDragSelecting) {
                            if (selectionMode && distance > 10f) {
                                isDragSelecting = true
                                val startId = records[startIndex].id
                                onSetSelectedIds(initialSelected + startId)
                            }
                        }

                        if (isDragSelecting) {
                            pointerChange.consume()
                            val currentIndex = findItemIndexAtOffset(gridState, currentOffset)
                            if (currentIndex != null) {
                                val minIdx = minOf(startIndex, currentIndex)
                                val maxIdx = maxOf(startIndex, currentIndex)
                                val draggedIds = (minIdx..maxIdx).mapNotNull { records.getOrNull(it)?.id }.toSet()
                                onSetSelectedIds(initialSelected + draggedIds)
                            }

                            // 边界滑动自动滚屏
                            val viewportHeight = gridState.layoutInfo.viewportSize.height
                            if (currentOffset.y < 120f) {
                                coroutineScope.launch { gridState.scrollBy(-25f) }
                            } else if (currentOffset.y > viewportHeight - 120f) {
                                coroutineScope.launch { gridState.scrollBy(25f) }
                            }
                        }
                    }
                }
            },
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(records, key = { it.id }) { record ->
            RecordCard(
                record = record,
                photoUri = "file://${photoRoot.absoluteFileOf(record.photoPath).absolutePath}",
                isSelected = record.id in selectedIds,
                selectionMode = selectionMode,
                onClick = {
                    if (selectionMode) onToggleSelection(record.id)
                    else onOpenRecord(record.id)
                },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(300),
                    fadeOutSpec = tween(300),
                    placementSpec = spring(
                        stiffness = Spring.StiffnessMediumLow,
                        dampingRatio = Spring.DampingRatioMediumBouncy
                    )
                )
            )
        }
    }
}

/**
 * 根据触控点 Offset 命中测试 LazyVerticalGrid 中的 VisibleItem 索引。
 */
private fun findItemIndexAtOffset(gridState: LazyGridState, offset: Offset): Int? {
    val itemsInfo = gridState.layoutInfo.visibleItemsInfo
    if (itemsInfo.isEmpty()) return null

    val hit = itemsInfo.firstOrNull { item ->
        val x = item.offset.x
        val y = item.offset.y
        offset.x >= x && offset.x <= x + item.size.width &&
            offset.y >= y && offset.y <= y + item.size.height
    }
    if (hit != null) return hit.index

    val firstVisible = itemsInfo.first()
    if (offset.y < firstVisible.offset.y) {
        return firstVisible.index
    }

    val lastVisible = itemsInfo.last()
    val lastBottom = lastVisible.offset.y + lastVisible.size.height
    if (offset.y > lastBottom) {
        return lastVisible.index
    }

    val rowMatch = itemsInfo.minByOrNull { item ->
        val itemCenterY = item.offset.y + item.size.height / 2f
        kotlin.math.abs(offset.y - itemCenterY)
    }
    return rowMatch?.index
}

@Composable
private fun RecordCard(
    record: DateRecord,
    photoUri: String,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardScale by animateFloatAsState(
        targetValue = if (selectionMode && isSelected) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "record_card_scale"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selectionMode && isSelected) 2.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "record_card_border_width"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selectionMode && isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(200),
        label = "record_card_border_color"
    )
    val cardShape = RoundedCornerShape(8.dp)
    Card(
        onClick = onClick,
        shape = cardShape,
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = cardScale
                scaleY = cardScale
            }
    ) {
        Box {
            AsyncImage(
                uri = photoUri,
                contentDescription = record.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )

            // 底部渐变蒙层，保证叠加文字在各种照片上的可读性
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.65f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${record.shootDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // 多选态下的复选角标（淡入淡出及缩放动画）
            this@Card.AnimatedVisibility(
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

@Composable
private fun SelectionBadge(isSelected: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.3f),
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
        Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
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

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "还没有记录",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "点击右下角按钮拍摄第一条记录",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
