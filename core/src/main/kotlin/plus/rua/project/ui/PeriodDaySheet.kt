@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import plus.rua.project.PeriodDocument
import plus.rua.project.PeriodForecast
import plus.rua.project.PeriodMood
import plus.rua.project.noteLength

/**
 * 单日面板：经期开关、心情与备注。
 *
 * 经期开关立即生效；心情和备注在点击「完成」或下滑、点遮罩关闭时，有变化才通过 [onSaveNote] 保存一次。
 *
 * @param date 面板对应的日期
 * @param today 今天；晚于今天的日期不能标记经期
 * @param document 包含未同步修改的经期文档
 * @param forecast 预测结果，用于显示当天所处阶段
 * @param onSetPeriod 切换经期开关时立即触发，参数为新的开关状态
 * @param onSaveNote 关闭面板且心情或备注有变化时触发
 * @param onDismiss 面板完全关闭后触发
 */
@Composable
internal fun PeriodDaySheet(
    date: LocalDate,
    today: LocalDate,
    document: PeriodDocument,
    forecast: PeriodForecast,
    onSetPeriod: (Boolean) -> Unit,
    onSaveNote: (PeriodMood?, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val saved = document.noteAt(date)
    var mood by rememberSaveable(date) { mutableStateOf(saved?.mood) }
    var text by rememberSaveable(date) { mutableStateOf(saved?.text.orEmpty()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        if (mood != saved?.mood || text.trim() != saved?.text.orEmpty()) onSaveNote(mood, text)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    val range = document.rangeAt(date, today)
    val isPeriod = date <= today && range != null
    val phase =
        when {
            isPeriod -> "经期第 ${range.start.daysUntil(date) + 1} 天"
            forecast.isPredictedPeriod(date) -> "预测经期"
            forecast.isOvulation(date) -> "排卵日（预测）"
            else -> null
        }
    val palette = periodPalette()

    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = sheetState,
        modifier = Modifier.testTag("period_day_sheet"),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${date.periodLabel(today.year)} ${date.weekdayLabel()}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (phase != null) {
                    Text(phase, style = MaterialTheme.typography.bodyMedium, color = palette.period)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("经期", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (date > today) "未来的日期不能标记经期" else "标记这一天是否在经期",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isPeriod,
                    onCheckedChange = onSetPeriod,
                    enabled = date <= today,
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.period, checkedThumbColor = palette.onPeriod),
                    modifier = Modifier.testTag("period_day_switch"),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("心情", style = MaterialTheme.typography.titleMedium)
                PeriodMoodRow(selected = mood, onSelect = { mood = if (mood == it) null else it })
            }

            OutlinedTextField(
                value = text,
                onValueChange = { if (it.noteLength() <= PeriodDocument.MAX_NOTE_CHARS) text = it },
                label = { Text("备注") },
                placeholder = { Text("记录一下今天的身体和心情") },
                supportingText = { Text("${text.noteLength()}/${PeriodDocument.MAX_NOTE_CHARS}") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag("period_note_field"),
            )

            Button(onClick = ::close, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("完成")
            }
        }
    }
}

/**
 * 五种心情的单选行，再次点击已选心情由调用方决定是否取消。
 *
 * @param selected 当前选中的心情，null 表示未选
 * @param onSelect 点击某个心情时触发
 * @param modifier 布局修饰符
 */
@Composable
internal fun PeriodMoodRow(
    selected: PeriodMood?,
    onSelect: (PeriodMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        PeriodMood.entries.forEach { mood ->
            val isSelected = mood == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    onClick = { onSelect(mood) },
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.secondary) else null,
                    modifier =
                    Modifier
                        .size(48.dp)
                        .testTag("period_mood_${mood.key}")
                        .semantics {
                            role = Role.RadioButton
                            this.selected = isSelected
                            contentDescription = mood.label
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(mood.emoji, fontSize = 22.sp)
                    }
                }
                Text(
                    text = mood.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
