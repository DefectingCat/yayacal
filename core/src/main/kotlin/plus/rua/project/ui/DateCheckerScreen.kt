@file:OptIn(ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private data class ExpiryRow(val id: Int, val days: Int? = null)

private sealed class DatePickerTarget {
    data object Production : DatePickerTarget()
    data class Row(val rowId: Int) : DatePickerTarget()
}

private enum class ExpiryStatus {
    UNKNOWN, SAFE, WARNING, URGENT, EXPIRED
}

@Composable
private fun ExpiryStatus.color(): Color = when (this) {
    ExpiryStatus.SAFE -> Color(0xFF059669)
    ExpiryStatus.WARNING -> Color(0xFFD97706)
    ExpiryStatus.URGENT -> Color(0xFFEA580C)
    ExpiryStatus.EXPIRED -> Color(0xFFDC2626)
    ExpiryStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Composable
private fun ExpiryStatus.containerColor(): Color = when (this) {
    ExpiryStatus.SAFE -> Color(0xFFD1FAE5)
    ExpiryStatus.WARNING -> Color(0xFFFEF3C7)
    ExpiryStatus.URGENT -> Color(0xFFFFEDD5)
    ExpiryStatus.EXPIRED -> Color(0xFFFEE2E2)
    ExpiryStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * 日期检查器页面，商品过期检查工具。
 *
 * 顶部设置生产日期，下方多行显示天数与到期日期的双向联动计算。
 * 支持删除行、FAB 添加新行，并显示每条保质期的过期状态。
 *
 * @param onBack 返回回调
 * @param modifier 布局修饰符
 */
@Composable
fun DateCheckerScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val today = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) }

    var productionDate by remember { mutableStateOf(today) }
    var rows by remember {
        mutableStateOf(
            listOf(
                ExpiryRow(0, 30),
                ExpiryRow(1, 60),
                ExpiryRow(2, 180)
            )
        )
    }
    var nextId by remember { mutableIntStateOf(3) }
    var pendingDeleteIds by remember { mutableStateOf(setOf<Int>()) }

    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "日期检查器",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val newId = nextId
                    rows = rows + ExpiryRow(newId, null)
                    nextId++
                    scope.launch {
                        delay(100)
                        scrollState.animateScrollTo(Int.MAX_VALUE)
                    }
                },
                modifier = Modifier.testTag("date_checker_fab"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                PlusIcon(color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ProductionDateCard(
                date = productionDate,
                isToday = productionDate == today,
                onClick = {
                    datePickerTarget = DatePickerTarget.Production
                    showDatePicker = true
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "保质期列表",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${rows.size} 项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .animateContentSize(
                        animationSpec = androidx.compose.animation.core.spring(
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                rows.forEachIndexed { index, row ->
                    val isBeingDeleted = row.id in pendingDeleteIds

                    key(row.id) {
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDeleteIds = pendingDeleteIds + row.id
                                    true
                                } else {
                                    false
                                }
                            }
                        )

                        var visible by remember { mutableStateOf(false) }

                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            visible = true
                        }

                        val expiryDate = row.days?.let { productionDate.plus(DatePeriod(days = it)) }
                        val daysRemaining = expiryDate?.let { today.daysUntil(it) }
                        val status = when {
                            daysRemaining == null -> ExpiryStatus.UNKNOWN
                            daysRemaining < 0 -> ExpiryStatus.EXPIRED
                            daysRemaining == 0 -> ExpiryStatus.URGENT
                            daysRemaining <= 7 -> ExpiryStatus.URGENT
                            daysRemaining <= 30 -> ExpiryStatus.WARNING
                            else -> ExpiryStatus.SAFE
                        }

                        AnimatedVisibility(
                            visible = visible && !isBeingDeleted,
                            enter = expandVertically(
                                expandFrom = Alignment.Bottom,
                                animationSpec = androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(animationSpec = androidx.compose.animation.core.tween(300)),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Text(
                                            text = "删除",
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            ) {
                                ExpiryCard(
                                    days = row.days,
                                    expiryDate = expiryDate,
                                    daysRemaining = daysRemaining,
                                    status = status,
                                    onDaysChange = { newDays ->
                                        rows = rows.map {
                                            if (it.id == row.id) it.copy(days = newDays) else it
                                        }
                                    },
                                    onExpiryDateChange = { newDate ->
                                        val newDays = productionDate.daysUntil(newDate)
                                        rows = rows.map {
                                            if (it.id == row.id) it.copy(days = newDays) else it
                                        }
                                    },
                                    onShowDatePicker = {
                                        datePickerTarget = DatePickerTarget.Row(row.id)
                                        showDatePicker = true
                                    }
                                )
                            }
                        }

                        if (isBeingDeleted) {
                            androidx.compose.runtime.LaunchedEffect(row.id) {
                                delay(400)
                                rows = rows.filter { it.id != row.id }
                                pendingDeleteIds = pendingDeleteIds - row.id
                            }
                        }
                    }

                    if (index < rows.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = when (val target = datePickerTarget) {
            is DatePickerTarget.Production -> productionDate.toEpochMillis()
            is DatePickerTarget.Row -> {
                val row = rows.find { it.id == target.rowId }
                row?.days?.let {
                    productionDate.plus(DatePeriod(days = it)).toEpochMillis()
                } ?: productionDate.toEpochMillis()
            }
            null -> productionDate.toEpochMillis()
        }

        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val selected = millis.toLocalDate()
                        when (val target = datePickerTarget) {
                            is DatePickerTarget.Production -> productionDate = selected
                            is DatePickerTarget.Row -> {
                                val newDays = productionDate.daysUntil(selected)
                                rows = rows.map {
                                    if (it.id == target.rowId) it.copy(days = newDays) else it
                                }
                            }
                            null -> {}
                        }
                    }
                    showDatePicker = false
                }) {
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

@Composable
private fun ProductionDateCard(
    date: LocalDate,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                CalendarIcon(
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column {
                Text(
                    text = "生产日期",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = date.formatChinese(),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (isToday) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "今天 · ${date.dayOfWeekChinese()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = date.dayOfWeekChinese(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpiryCard(
    days: Int?,
    expiryDate: LocalDate?,
    daysRemaining: Int?,
    status: ExpiryStatus,
    onDaysChange: (Int?) -> Unit,
    onExpiryDateChange: (LocalDate) -> Unit,
    onShowDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var daysText by remember(days) { mutableStateOf(days?.toString() ?: "") }
    var dateText by remember(expiryDate) { mutableStateOf(expiryDate?.toString() ?: "") }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { newValue ->
                        daysText = newValue.filter { it.isDigit() }.take(4)
                        onDaysChange(daysText.toIntOrNull())
                    },
                    label = { Text("天数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                ArrowRightIcon(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { newValue ->
                        dateText = newValue
                        try {
                            onExpiryDateChange(LocalDate.parse(newValue))
                        } catch (_: Exception) {
                        }
                    },
                    label = { Text("到期日期") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = onShowDatePicker,
                            modifier = Modifier.size(32.dp)
                        ) {
                            CalendarIcon(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.weight(1.8f),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            if (daysRemaining != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val statusText = when {
                    daysRemaining < 0 -> "已过期 ${-daysRemaining} 天"
                    daysRemaining == 0 -> "今天过期"
                    daysRemaining == 1 -> "明天过期"
                    else -> "还有 $daysRemaining 天"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(status.containerColor())
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = status.color(),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(status.color())
                    )
                }
            }
        }

    }
}

// region Icons

@Composable
private fun BackArrowIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.75f, size.height * 0.15f),
            end = Offset(size.width * 0.25f, size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.25f, size.height * 0.5f),
            end = Offset(size.width * 0.75f, size.height * 0.85f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 2.dp.toPx()
        val cx = size.width / 2
        val cy = size.height / 2
        val half = size.minDimension * 0.35f
        drawLine(
            color = color,
            start = Offset(cx, cy - half),
            end = Offset(cx, cy + half),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(cx - half, cy),
            end = Offset(cx + half, cy),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CalendarIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 1.5f.dp.toPx()
        val pad = 3.dp.toPx()
        val topY = pad + 4.dp.toPx()
        val bottomY = size.height - pad
        val leftX = pad
        val rightX = size.width - pad

        drawLine(color, Offset(leftX, topY), Offset(rightX, topY), strokeWidth)
        drawLine(color, Offset(leftX, topY), Offset(leftX, bottomY), strokeWidth)
        drawLine(color, Offset(rightX, topY), Offset(rightX, bottomY), strokeWidth)
        drawLine(color, Offset(leftX, bottomY), Offset(rightX, bottomY), strokeWidth)

        val h1 = size.width * 0.3f
        val h2 = size.width * 0.7f
        drawLine(color, Offset(h1, pad), Offset(h1, topY), strokeWidth)
        drawLine(color, Offset(h2, pad), Offset(h2, topY), strokeWidth)
    }
}

@Composable
private fun ArrowRightIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        val y = size.height / 2
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width * 0.65f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, y - size.height * 0.3f),
            end = Offset(size.width * 0.65f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, y + size.height * 0.3f),
            end = Offset(size.width * 0.65f, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

// endregion

// region Helpers

private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

@Suppress("DEPRECATION") // monthNumber/dayOfMonth 无替代 API，kotlinx-datetime 尚未提供新接口
private fun LocalDate.formatChinese(): String =
    "${year}年${monthNumber}月${dayOfMonth}日"

@Suppress("DEPRECATION", "Unused") // monthNumber/dayOfMonth 无替代 API
private fun LocalDate.formatShortChinese(): String =
    "${monthNumber}月${dayOfMonth}日"

private fun LocalDate.dayOfWeekChinese(): String = when (dayOfWeek.ordinal) {
    0 -> "周一"
    1 -> "周二"
    2 -> "周三"
    3 -> "周四"
    4 -> "周五"
    5 -> "周六"
    6 -> "周日"
    else -> ""
}

// endregion
