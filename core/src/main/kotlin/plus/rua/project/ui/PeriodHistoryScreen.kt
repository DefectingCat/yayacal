package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import plus.rua.project.PeriodDocument
import plus.rua.project.PeriodRange
import plus.rua.project.PeriodUiState
import plus.rua.project.PeriodViewModel
import plus.rua.project.recentCycleLengths

/**
 * 经期历史页（有状态版本）。
 *
 * @param onBack 点击左上角返回时触发
 * @param viewModel 经期页面共用的 ViewModel，默认按应用 Context 创建
 * @param modifier 布局修饰符
 */
@Composable
fun PeriodHistoryScreen(
    onBack: () -> Unit,
    viewModel: PeriodViewModel = periodViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    PeriodHistoryScreen(
        uiState = uiState,
        message = message,
        onBack = onBack,
        onUpdateRange = viewModel::updateRange,
        onDeleteRange = viewModel::deleteRange,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

private data class PeriodHistoryItem(
    val range: PeriodRange,
    val periodDays: Int,
    val cycleDays: Int?,
    val isLatest: Boolean,
)

/**
 * 经期历史页（无状态版本）：按时间倒序列出每次经期，点击修改或删除。
 *
 * @param uiState 经期界面状态
 * @param message 一次性提示，显示为 Snackbar
 * @param onBack 点击左上角返回时触发
 * @param onUpdateRange 在修改对话框点击「保存」时触发，参数为原开始日、新开始日、新结束日（null 表示进行中）
 * @param onDeleteRange 在删除确认框点击「删除」时触发，参数为该次经期的开始日
 * @param onDismissMessage [message] 显示完毕后触发
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodHistoryScreen(
    uiState: PeriodUiState,
    message: String?,
    onBack: () -> Unit,
    onUpdateRange: (LocalDate, LocalDate, LocalDate?) -> Unit,
    onDeleteRange: (LocalDate) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = uiState.today
    val ranges = uiState.document.ranges
    val items =
        ranges
            .mapIndexed { index, range ->
                PeriodHistoryItem(
                    range = range,
                    periodDays = range.start.daysUntil(range.endOn(today)) + 1,
                    cycleDays = ranges.getOrNull(index + 1)?.let { range.start.daysUntil(it.start) },
                    isLatest = index == ranges.lastIndex,
                )
            }.reversed()
    var editing by remember { mutableStateOf<PeriodHistoryItem?>(null) }
    var deleting by remember { mutableStateOf<PeriodRange?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }
    }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = { Text("历史记录", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有经期记录", style = MaterialTheme.typography.titleMedium)
                Text(
                    "在首页点「记录上次经期」开始记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "trend") {
                PeriodCycleTrendCard(
                    cycles = uiState.document.recentCycleLengths(),
                    forecast = uiState.forecast,
                    currentYear = today.year,
                )
            }
            items(items, key = { it.range.start.toString() }) { item ->
                PeriodHistoryCard(
                    item = item,
                    today = today,
                    typicalCycle = uiState.forecast.cycleLength,
                    onClick = { editing = item },
                )
            }
        }
    }

    editing?.let { item ->
        EditPeriodDialog(
            item = item,
            today = today,
            periodLength = uiState.forecast.periodLength,
            onSave = { start, end ->
                editing = null
                onUpdateRange(item.range.start, start, end)
            },
            onDelete = {
                editing = null
                deleting = item.range
            },
            onDismiss = { editing = null },
        )
    }

    deleting?.let { range ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这次经期？") },
            text = { Text("${rangeLabel(range, today)} 的经期记录会从两台手机上一起删除，心情和备注会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDeleteRange(range.start)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

private fun rangeLabel(
    range: PeriodRange,
    today: LocalDate,
): String {
    val end = range.end
    return if (end == null) {
        "${range.start.periodLabel(today.year)} – 进行中"
    } else {
        "${range.start.periodLabel(today.year)} – ${end.periodLabel(range.start.year)}"
    }
}

@Composable
private fun PeriodHistoryCard(
    item: PeriodHistoryItem,
    today: LocalDate,
    typicalCycle: Int,
    onClick: () -> Unit,
) {
    val palette = periodPalette()
    val cycleText =
        when {
            item.cycleDays != null -> "周期 ${item.cycleDays} 天"
            else -> "当前周期第 ${item.range.start.daysUntil(today) + 1} 天"
        }
    // 横条长度按周期天数分配；最近一段用典型周期估计
    val cycle = (item.cycleDays ?: maxOf(typicalCycle, item.range.start.daysUntil(today) + 1)).coerceAtLeast(item.periodDays)
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().testTag("period_history_${item.range.start}"),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rangeLabel(item.range, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "经期 ${item.periodDays} 天 · $cycleText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            ) {
                Box(Modifier.weight(item.periodDays.toFloat()).fillMaxSize().background(palette.period))
                if (cycle > item.periodDays) {
                    Box(
                        Modifier
                            .weight((cycle - item.periodDays).toFloat())
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
        }
    }
}

private enum class EditField { START, END }

@Composable
private fun EditPeriodDialog(
    item: PeriodHistoryItem,
    today: LocalDate,
    periodLength: Int,
    onSave: (LocalDate, LocalDate?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var start by remember { mutableStateOf(item.range.start) }
    var ongoing by remember { mutableStateOf(item.range.isOngoing) }
    var end by remember { mutableStateOf(item.range.end ?: minOf(today, start.plus(DatePeriod(days = periodLength - 1)))) }
    var picking by remember { mutableStateOf<EditField?>(null) }
    val endRange = start..minOf(today, PeriodDocument.latestEndFor(start))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改经期") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DateRow("开始日期", start.periodLabel(today.year)) { picking = EditField.START }
                if (!ongoing) {
                    DateRow("结束日期", end.periodLabel(today.year)) { picking = EditField.END }
                }
                if (item.isLatest) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ongoing = !ongoing }) {
                        Checkbox(checked = ongoing, onCheckedChange = { ongoing = it })
                        Text("仍在进行中", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(start, if (ongoing) null else end.coerceIn(endRange.start, endRange.endInclusive)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )

    when (picking) {
        EditField.START -> {
            PeriodDatePickerDialog(
                title = "经期从哪天开始",
                initial = start,
                range = PeriodDocument.DATE_BOUNDS.start..today,
                onConfirm = {
                    start = it
                    end = end.coerceIn(it, minOf(today, PeriodDocument.latestEndFor(it)))
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }

        EditField.END -> {
            PeriodDatePickerDialog(
                title = "经期在哪天结束",
                initial = end,
                range = endRange,
                onConfirm = {
                    end = it
                    picking = null
                },
                onDismiss = { picking = null },
            )
        }

        null -> {}
    }
}

@Composable
private fun DateRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}
