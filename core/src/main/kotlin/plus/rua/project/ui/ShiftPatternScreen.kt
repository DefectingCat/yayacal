package plus.rua.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.CalendarViewModel
import plus.rua.project.ShiftKind
import plus.rua.project.ShiftPattern
import plus.rua.project.ShiftPatternStorage
import kotlin.time.Instant

/**
 * 班次设置页。照抄 DateCheckerScreen 的 storage 创建 + 自动存盘模式。
 *
 * 用户在页面内修改 → [LaunchedEffect] 自动存 storage → 返回主界面后
 * CalendarViewModel.onResume 重读 → 日历立即刷新。
 *
 * 页面结构三段:
 * 1. 基础周期:锚点日期、当前周期展示;
 * 2. 预设方案:1班1休 / 2班2休 / 3班3休 / 4班4休 快捷切换;
 * 3. 调班设置:迷你月历(点=翻转班/休,长按=设/清相位断点)+ 恢复默认。
 *
 * @param onBack 返回回调(由 Activity 触发 finishWithSlideBack)
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ShiftPatternScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val storage = remember { ShiftPatternStorage.fromContext(context) }
    val saved = remember { storage.load() }
    var pattern by remember { mutableStateOf(saved ?: CalendarViewModel.DEFAULT_PATTERN) }
    LaunchedEffect(pattern) { storage.save(pattern) }

    var showAnchorPicker by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("班次设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("基础周期", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("锚点日期", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { showAnchorPicker = true }) {
                            Text(pattern.anchorDate.toString())
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("周期", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            pattern.cycle.joinToString(" ") { if (it == ShiftKind.WORK) "班" else "休" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text("预设方案", style = MaterialTheme.typography.labelLarge)
            val presets = listOf(
                "1班1休" to listOf(ShiftKind.WORK, ShiftKind.OFF),
                "2班2休" to listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF),
                "3班3休" to listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF, ShiftKind.OFF),
                "4班4休" to listOf(ShiftKind.WORK, ShiftKind.WORK, ShiftKind.WORK, ShiftKind.WORK, ShiftKind.OFF, ShiftKind.OFF, ShiftKind.OFF, ShiftKind.OFF)
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (label, cycle) ->
                    FilterChip(
                        selected = pattern.cycle == cycle,
                        onClick = {
                            pattern = pattern.copy(
                                cycle = cycle,
                                overrides = emptyMap(),
                                phaseBreaks = emptyList()
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }

            Text("调班设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            ShiftCalendarGrid(pattern = pattern, onPatternChange = { pattern = it })
            Text(
                text = "点某天 = 翻转班/休,长按某天 = 设/清相位断点",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("恢复默认")
            }
        }
    }

    if (showAnchorPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = pattern.anchorDate.toEpochMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showAnchorPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { pattern = pattern.copy(anchorDate = it.toLocalDate()) }
                    showAnchorPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAnchorPicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("恢复默认") },
            text = { Text("将清空所有调班与断点设置,恢复为 2 班 2 休。确认?") },
            confirmButton = {
                TextButton(onClick = {
                    pattern = CalendarViewModel.DEFAULT_PATTERN
                    showResetDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("取消") }
            }
        )
    }
}

private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
