package plus.rua.project.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
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
 * - TopAppBar 右侧"排序"菜单：6 种排序组合
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
                    Text(
                        text = if (uiState.selectionMode) {
                            "已选 ${uiState.selectedIds.size}"
                        } else {
                            "日期记录器"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.selectionMode) viewModel.toggleSelectionMode()
                        else onBack()
                    }) {
                        Icon(
                            imageVector = if (uiState.selectionMode) Icons.Filled.Close
                            else Icons.Filled.ChevronLeft,
                            contentDescription = if (uiState.selectionMode) "退出多选" else "返回"
                        )
                    }
                },
                actions = {
                    if (uiState.selectionMode) {
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
                                sortMenuItems().forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text(item.label) },
                                        onClick = {
                                            viewModel.setSortOrder(item.order)
                                            showSortMenu = false
                                        },
                                        leadingIcon = if (uiState.sortOrder == item.order) {
                                            { Icon(Icons.Filled.Check, contentDescription = null) }
                                        } else null
                                    )
                                }
                            }
                        }
                        IconButton(onClick = viewModel::toggleSelectionMode) {
                            Icon(Icons.Filled.Checklist, contentDescription = "多选")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            if (uiState.selectionMode) {
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
            if (!uiState.selectionMode) {
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
        containerColor = MaterialTheme.colorScheme.surface
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
                    onToggleSelection = viewModel::toggleSelection
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

private data class SortMenuItem(val order: RecordSortOrder, val label: String)

private fun sortMenuItems(): List<SortMenuItem> = listOf(
    SortMenuItem(RecordSortOrder(RecordSortField.SHOOT_DATE, false), "拍摄日期 · 新→旧"),
    SortMenuItem(RecordSortOrder(RecordSortField.SHOOT_DATE, true), "拍摄日期 · 旧→新"),
    SortMenuItem(RecordSortOrder(RecordSortField.LINKED_DATE, false), "关联日期 · 新→旧"),
    SortMenuItem(RecordSortOrder(RecordSortField.LINKED_DATE, true), "关联日期 · 旧→新"),
    SortMenuItem(RecordSortOrder(RecordSortField.CREATED_AT, false), "创建时间 · 新→旧"),
    SortMenuItem(RecordSortOrder(RecordSortField.CREATED_AT, true), "创建时间 · 旧→新")
)

@Composable
private fun RecordGrid(
    records: List<DateRecord>,
    selectedIds: Set<Long>,
    selectionMode: Boolean,
    photoRoot: DateRecorderRepository,
    onOpenRecord: (Long) -> Unit,
    onToggleSelection: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                }
            )
        }
    }
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
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Box {
            Column {
                AsyncImage(
                    uri = photoUri,
                    contentDescription = record.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                )
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1
                    )
                    Text(
                        text = "${record.shootDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 多选态下的复选角标
            if (selectionMode) {
                SelectionBadge(
                    isSelected = isSelected,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectionBadge(isSelected: Boolean, modifier: Modifier = Modifier) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary
    else Color.Black.copy(alpha = 0.3f)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .padding(4.dp)
    ) {
        if (isSelected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
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
