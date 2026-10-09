package plus.rua.project.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import plus.rua.project.PeriodDocument
import plus.rua.project.PeriodForecast
import plus.rua.project.PeriodMood
import plus.rua.project.PeriodNote
import plus.rua.project.PeriodStatus
import plus.rua.project.PeriodSyncStatus
import plus.rua.project.PeriodUiState
import plus.rua.project.PeriodViewModel

/**
 * 经期记录首页（有状态版本），页面回到前台时刷新「今天」并同步。
 *
 * @param onBack 点击左上角返回时触发
 * @param onOpenHistory 点击顶栏历史图标或周期摘要卡片时触发
 * @param onOpenSettings 点击顶栏设置图标时触发
 * @param viewModel 经期页面共用的 ViewModel，默认按应用 Context 创建
 * @param modifier 布局修饰符
 */
@Composable
fun PeriodTrackerScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PeriodViewModel = periodViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    PeriodTrackerScreen(
        uiState = uiState,
        message = message,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onOpenSettings = onOpenSettings,
        onStartPeriod = viewModel::startPeriod,
        onEndPeriod = viewModel::endPeriod,
        onSetPeriodDay = viewModel::setPeriodDay,
        onSaveNote = viewModel::saveNote,
        onRetrySync = viewModel::retrySync,
        onDiscardPending = viewModel::discardPending,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

/** 按应用 Context 创建经期页面共用的 ViewModel。 */
@Composable
internal fun periodViewModel(): PeriodViewModel {
    val context = LocalContext.current.applicationContext
    return viewModel(factory = viewModelFactory { initializer { PeriodViewModel.fromContext(context) } })
}

private enum class PeriodPicker { START, END }

/**
 * 经期记录首页（无状态版本）：状态卡、今天的心情、月历和周期摘要。
 *
 * @param uiState 经期界面状态
 * @param message 一次性提示，显示为 Snackbar
 * @param onBack 点击左上角返回时触发
 * @param onOpenHistory 点击顶栏历史图标或周期摘要卡片时触发
 * @param onOpenSettings 点击顶栏设置图标时触发
 * @param onStartPeriod 点击「经期来了」或在日期选择中确认开始日期时触发
 * @param onEndPeriod 点击「经期结束」或在日期选择中确认结束日期时触发
 * @param onSetPeriodDay 单日面板切换经期开关、或批量编辑点按日期时触发
 * @param onSaveNote 选择今天的心情，或关闭单日面板且心情、备注有变化时触发
 * @param onRetrySync 点击同步状态或在同步说明中选择重试时触发
 * @param onDiscardPending 同步被拒绝后，在说明对话框中确认放弃未同步修改时触发
 * @param onDismissMessage [message] 显示完毕后触发
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodTrackerScreen(
    uiState: PeriodUiState,
    message: String?,
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartPeriod: (LocalDate) -> Unit,
    onEndPeriod: (LocalDate) -> Unit,
    onSetPeriodDay: (LocalDate, Boolean) -> Unit,
    onSaveNote: (LocalDate, PeriodMood?, String) -> Unit,
    onRetrySync: () -> Unit,
    onDiscardPending: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = uiState.today
    val document = uiState.document
    val forecast = uiState.forecast
    var sheetDate by rememberSaveable { mutableStateOf<String?>(null) }
    var picker by rememberSaveable { mutableStateOf<PeriodPicker?>(null) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var showSyncDetail by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }
    }

    val startRange = startPickerRange(document, today)
    val ongoing = document.ongoing
    val endRange = ongoing?.let { it.start..minOf(today, PeriodDocument.latestEndFor(it.start)) }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = { Text("经期记录", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, contentDescription = "返回") }
                },
                actions = {
                    val label = periodSyncLabel(uiState.sync, uiState.pendingCount)
                    TextButton(
                        onClick = {
                            val sync = uiState.sync
                            if (sync is PeriodSyncStatus.Failed || sync is PeriodSyncStatus.Rejected || sync is PeriodSyncStatus.Outdated) {
                                showSyncDetail = true
                            } else {
                                onRetrySync()
                            }
                        },
                        modifier = Modifier.testTag("period_sync_status"),
                    ) {
                        val color = if (label.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        Icon(label.icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label.text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Outlined.History, contentDescription = "历史记录") }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "经期设置") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PeriodStatusCard(
                forecast = forecast,
                today = today,
                onPrimary = {
                    when (forecast.status) {
                        is PeriodStatus.NoData, is PeriodStatus.ForgotToEnd -> picker = if (ongoing == null) PeriodPicker.START else PeriodPicker.END
                        is PeriodStatus.InPeriod -> onEndPeriod(today)
                        is PeriodStatus.Upcoming, is PeriodStatus.Late -> onStartPeriod(today)
                    }
                },
                onPickDate =
                when (forecast.status) {
                    is PeriodStatus.InPeriod -> {
                        { picker = PeriodPicker.END }
                    }

                    is PeriodStatus.Upcoming, is PeriodStatus.Late -> {
                        if (startRange.isEmpty()) null else ({ picker = PeriodPicker.START })
                    }

                    else -> {
                        null
                    }
                },
            )

            TodayMoodCard(
                note = document.noteAt(today),
                onMood = { mood -> onSaveNote(today, mood, document.noteAt(today)?.text.orEmpty()) },
                onOpenNote = { sheetDate = today.toString() },
            )

            PeriodCalendarGrid(
                document = document,
                forecast = forecast,
                today = today,
                isEditing = isEditing,
                onDayClick = { date ->
                    if (isEditing) {
                        onSetPeriodDay(date, document.rangeAt(date, today) == null)
                    } else {
                        sheetDate = date.toString()
                    }
                },
                onToggleEditing = { isEditing = !isEditing },
            )

            PeriodSummaryCard(forecast = forecast, onClick = onOpenHistory)

            Text(
                text = "预测基于历史记录，仅供参考，不能作为避孕或医疗依据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    sheetDate?.let(LocalDate::parse)?.let { date ->
        PeriodDaySheet(
            date = date,
            today = today,
            document = document,
            forecast = forecast,
            onSetPeriod = { onSetPeriodDay(date, it) },
            onSaveNote = { mood, text -> onSaveNote(date, mood, text) },
            onDismiss = { sheetDate = null },
        )
    }

    when (picker) {
        PeriodPicker.START -> {
            PeriodDatePickerDialog(
                title = if (document.ranges.isEmpty()) "上次经期从哪天开始" else "经期从哪天开始",
                initial = today,
                range = startRange,
                onConfirm = {
                    picker = null
                    onStartPeriod(it)
                },
                onDismiss = { picker = null },
            )
        }

        PeriodPicker.END -> {
            if (ongoing != null && endRange != null) {
                val suggested = ongoing.start.plus(DatePeriod(days = forecast.periodLength - 1))
                PeriodDatePickerDialog(
                    title = "经期在哪天结束",
                    initial = if (forecast.status is PeriodStatus.ForgotToEnd) suggested else today,
                    range = endRange,
                    onConfirm = {
                        picker = null
                        onEndPeriod(it)
                    },
                    onDismiss = { picker = null },
                )
            } else {
                // 进行中的经期已被其他设备结束，关闭失效的选择框
                LaunchedEffect(Unit) { picker = null }
            }
        }

        null -> {}
    }

    if (showSyncDetail) {
        PeriodSyncDialog(
            sync = uiState.sync,
            pendingCount = uiState.pendingCount,
            onRetry = {
                showSyncDetail = false
                onRetrySync()
            },
            onDiscard = {
                showSyncDetail = false
                onDiscardPending()
            },
            onDismiss = { showSyncDetail = false },
        )
    }
}

/** 开始日期可选范围：晚于最近一段经期的结束日，且不晚于今天。 */
private fun startPickerRange(
    document: PeriodDocument,
    today: LocalDate,
): ClosedRange<LocalDate> {
    val latestEnd = document.ranges.lastOrNull()?.end
    val earliest = latestEnd?.let { PeriodDocument.DATE_BOUNDS.start.coerceAtLeast(it.plus(DatePeriod(days = 1))) } ?: PeriodDocument.DATE_BOUNDS.start
    return earliest..today
}

private data class PeriodStatusContent(
    val ringValue: String,
    val ringCaption: String,
    val title: String,
    val subtitle: String,
    val primary: String,
)

private fun statusContent(
    forecast: PeriodForecast,
    today: LocalDate,
): PeriodStatusContent = when (val status = forecast.status) {
    PeriodStatus.NoData -> {
        PeriodStatusContent("—", "未记录", "还没有记录", "记录一次经期后开始预测", "记录上次经期")
    }

    is PeriodStatus.InPeriod -> {
        PeriodStatusContent(
            "第${status.day}天",
            "经期",
            "经期中",
            "预计 ${status.expectedEnd.periodLabel(today.year)} 结束",
            "经期结束",
        )
    }

    is PeriodStatus.ForgotToEnd -> {
        PeriodStatusContent(
            "第${status.day}天",
            "经期",
            "是否忘记结束？",
            "经期已持续 ${status.day} 天，补记结束日让预测更准",
            "补记结束日",
        )
    }

    is PeriodStatus.Upcoming -> {
        val phase =
            when {
                status.isOvulation -> " · 今天是排卵日（预测）"
                status.isFertile -> " · 当前处于易孕期（预测）"
                else -> ""
            }
        PeriodStatusContent(
            if (status.daysUntil == 0) "今天" else "${status.daysUntil}",
            if (status.daysUntil == 0) "预计" else "天后",
            if (status.daysUntil == 0) "预计今天来经期" else "距下次经期 ${status.daysUntil} 天",
            "预计 ${status.nextStart.periodLabel(today.year)}$phase",
            "经期来了",
        )
    }

    is PeriodStatus.Late -> {
        PeriodStatusContent(
            "+${status.days}",
            "推迟",
            "已推迟 ${status.days} 天",
            "原预计 ${status.expectedStart.periodLabel(today.year)}",
            "经期来了",
        )
    }
}

@Composable
private fun PeriodStatusCard(
    forecast: PeriodForecast,
    today: LocalDate,
    onPrimary: () -> Unit,
    onPickDate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = periodPalette()
    val content = statusContent(forecast, today)
    val status = forecast.status
    val ringColor =
        when {
            status is PeriodStatus.Upcoming && (status.isFertile || status.isOvulation) -> palette.ovulation
            status is PeriodStatus.Upcoming -> MaterialTheme.colorScheme.primary
            else -> palette.period
        }
    Card(
        shape = PeriodCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth().testTag("period_status_card"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CycleRing(progress = forecast.cycleProgress, value = content.ringValue, caption = content.ringCaption, color = ringColor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(content.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(content.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onPrimary,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.period, contentColor = palette.onPeriod),
                        modifier = Modifier.testTag("period_primary_action"),
                    ) { Text(content.primary) }
                    if (onPickDate != null) {
                        TextButton(onClick = onPickDate) { Text("选择日期") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CycleRing(
    progress: Float,
    value: String,
    caption: String,
    color: Color,
) {
    val animated by animateFloatAsState(targetValue = progress, label = "cycleProgress")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayMoodCard(
    note: PeriodNote?,
    onMood: (PeriodMood?) -> Unit,
    onOpenNote: () -> Unit,
) {
    Card(
        shape = PeriodCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("今天的心情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenNote, modifier = Modifier.testTag("period_today_note")) {
                    Text(if (note?.text.isNullOrEmpty()) "写备注" else "编辑备注")
                }
            }
            PeriodMoodRow(
                selected = note?.mood,
                onSelect = { mood -> onMood(if (note?.mood == mood) null else mood) },
                modifier = Modifier.padding(end = 8.dp),
            )
            val noteText = note?.text
            if (!noteText.isNullOrEmpty()) {
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PeriodSummaryCard(
    forecast: PeriodForecast,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = PeriodCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().testTag("period_summary"),
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "周期约 ${forecast.cycleLength} 天 · 经期约 ${forecast.periodLength} 天",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                    if (forecast.usesDefaultCycle) {
                        "记录满 2 个周期后按你的记录预测，目前使用默认周期"
                    } else {
                        "规律性：${forecast.regularity.label}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "查看历史记录",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PeriodSyncDialog(
    sync: PeriodSyncStatus,
    pendingCount: Int,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (title, detail) =
        when (sync) {
            is PeriodSyncStatus.Outdated -> "服务器版本较旧" to "当前服务器还不支持经期同步。记录会先保存在本机，服务器升级后自动同步。"
            is PeriodSyncStatus.Rejected -> "同步被拒绝" to "服务器拒绝了本机的修改：${sync.message}。可以稍后重试，或放弃这 $pendingCount 项未同步的修改。"
            is PeriodSyncStatus.Failed -> "同步失败" to "${sync.message}。记录已保存在本机，联网后会自动同步。"
            else -> "同步" to "记录会在联网后自动同步。"
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = { TextButton(onClick = onRetry) { Text("重试") } },
        dismissButton = {
            if (sync is PeriodSyncStatus.Rejected && pendingCount > 0) {
                TextButton(onClick = onDiscard) { Text("放弃修改", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
    )
}
