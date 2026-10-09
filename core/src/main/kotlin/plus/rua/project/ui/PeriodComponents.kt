package plus.rua.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.PeriodDocument
import plus.rua.project.PeriodSyncStatus
import kotlin.time.Instant

/** 经期相关的固定语义色；不随系统动态取色变化，保证一眼能认出经期与排卵日。 */
@Immutable
internal data class PeriodPalette(
    val period: Color,
    val onPeriod: Color,
    val ovulation: Color,
)

private val LightPeriodPalette =
    PeriodPalette(
        period = Color(0xFFD94F70),
        onPeriod = Color.White,
        ovulation = Color(0xFF7A5AC8),
    )

private val DarkPeriodPalette =
    PeriodPalette(
        period = Color(0xFFFF8DA8),
        onPeriod = Color(0xFF5C1028),
        ovulation = Color(0xFFC9B6FF),
    )

/** 按当前主题的明暗选择经期配色。 */
@Composable
internal fun periodPalette(): PeriodPalette = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) DarkPeriodPalette else LightPeriodPalette

/** 经期页面卡片圆角，与工具页卡片一致。 */
internal val PeriodCardShape = RoundedCornerShape(24.dp)

private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 「10月9日」，不在 [currentYear] 时带上年份。 */
internal fun LocalDate.periodLabel(currentYear: Int? = null): String {
    val monthDay = "${month.number}月${day}日"
    return if (currentYear == null || year == currentYear) monthDay else "${year}年$monthDay"
}

/** 「周五」。 */
internal fun LocalDate.weekdayLabel(): String = "周" + WEEKDAYS[dayOfWeek.isoDayNumber - 1]

internal fun LocalDate.toPickerMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

internal fun Long.toPickerDate(): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

/**
 * 只允许选择 [range] 内日期的日期选择对话框。
 *
 * @param title 对话框标题，说明正在选择什么日期
 * @param initial 初始选中日期，超出 [range] 时取最近的边界
 * @param range 可选日期范围（闭区间）
 * @param onConfirm 点击「确定」时以选中日期触发
 * @param onDismiss 点击「取消」或对话框外部时触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodDatePickerDialog(
    title: String,
    initial: LocalDate,
    range: ClosedRange<LocalDate>,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectable =
        remember(range) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis.toPickerDate() in range

                override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
            }
        }
    val state =
        rememberDatePickerState(
            initialSelectedDateMillis = initial.coerceIn(range.start, range.endInclusive).toPickerMillis(),
            yearRange = range.start.year..range.endInclusive.year,
            selectableDates = selectable,
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let { onConfirm(it.toPickerDate()) } },
                enabled = state.selectedDateMillis != null,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DatePicker(
            state = state,
            title = { Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) },
        )
    }
}

/**
 * 在同一个日历里连续选择经期开始和结束日的对话框。
 *
 * 选了开始日后，结束日只能落在 31 天以内；点比开始日更早的日期会重新选择开始日。
 *
 * @param title 对话框标题
 * @param hint 标题下的说明，告诉用户只选开始日意味着什么
 * @param initialStart 初始开始日，为 null 时不预选
 * @param initialEnd 初始结束日，为 null 时只预选开始日
 * @param range 可选日期范围（闭区间）
 * @param allowOpenEnd 是否允许只选开始日（表示经期仍在进行）
 * @param onConfirm 点击「确定」时以开始日和结束日触发，结束日为 null 表示仍在进行
 * @param onDismiss 点击「取消」或对话框外部时触发
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodDateRangePickerDialog(
    title: String,
    hint: String,
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    range: ClosedRange<LocalDate>,
    allowOpenEnd: Boolean,
    onConfirm: (LocalDate, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 已选开始日放在快照状态里，日历格子读取它来限制结束日的范围
    val chosenStart = remember { mutableStateOf(initialStart) }
    val selectable =
        remember(range) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val date = utcTimeMillis.toPickerDate()
                    if (date !in range) return false
                    val start = chosenStart.value ?: return true
                    return date <= start || date <= PeriodDocument.latestEndFor(start)
                }

                override fun isSelectableYear(year: Int): Boolean = year in range.start.year..range.endInclusive.year
            }
        }
    val state =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialStart?.coerceIn(range.start, range.endInclusive)?.toPickerMillis(),
            initialSelectedEndDateMillis = initialEnd?.coerceIn(range.start, range.endInclusive)?.toPickerMillis(),
            initialDisplayedMonthMillis = (initialStart ?: range.endInclusive).toPickerMillis(),
            yearRange = range.start.year..range.endInclusive.year,
            selectableDates = selectable,
        )
    LaunchedEffect(state) {
        snapshotFlow { state.selectedStartDateMillis }.collect { chosenStart.value = it?.toPickerDate() }
    }
    val start = state.selectedStartDateMillis?.toPickerDate()
    val end = state.selectedEndDateMillis?.toPickerDate()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { start?.let { onConfirm(it, end) } },
                enabled = start != null && (end != null || allowOpenEnd),
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        DateRangePicker(
            state = state,
            title = {
                Column(Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title)
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            headline = {
                // 系统默认标题是英文，这里用中文实时显示已选的日期
                Text(
                    text = "${start?.periodLabel() ?: "开始日"} – ${end?.periodLabel() ?: if (allowOpenEnd && start != null) "进行中" else "结束日"}",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
            showModeToggle = false,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 顶栏同步状态的图标与文案。 */
internal data class PeriodSyncLabel(
    val icon: ImageVector,
    val text: String,
    val isError: Boolean,
)

internal fun periodSyncLabel(
    sync: PeriodSyncStatus,
    pendingCount: Int,
): PeriodSyncLabel = when {
    sync is PeriodSyncStatus.Syncing -> PeriodSyncLabel(Icons.Outlined.CloudSync, "同步中", false)
    sync is PeriodSyncStatus.Outdated -> PeriodSyncLabel(Icons.Outlined.CloudOff, "服务器版本较旧", true)
    sync is PeriodSyncStatus.Rejected -> PeriodSyncLabel(Icons.Outlined.ErrorOutline, "同步被拒绝", true)
    sync is PeriodSyncStatus.Failed -> PeriodSyncLabel(Icons.Outlined.CloudOff, "同步失败", true)
    pendingCount > 0 -> PeriodSyncLabel(Icons.Outlined.CloudUpload, "$pendingCount 项待同步", false)
    sync is PeriodSyncStatus.Synced -> PeriodSyncLabel(Icons.Outlined.CloudDone, "已同步", false)
    else -> PeriodSyncLabel(Icons.Outlined.CloudSync, "未同步", false)
}
