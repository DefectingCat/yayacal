@file:OptIn(ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
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

/**
 * 日期检查器页面，商品过期检查工具。
 *
 * 顶部设置生产日期，下方多行显示天数与到期日期的双向联动计算。
 * 支持滑动删除行、FAB 添加新行。
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

    var showDatePicker by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<DatePickerTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日期检查器") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        BackArrowIcon()
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    rows = rows + ExpiryRow(nextId, null)
                    nextId++
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                PlusIcon(color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "生产日期",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            ProductionDateField(
                date = productionDate,
                onDateChange = { productionDate = it },
                onShowDatePicker = {
                    datePickerTarget = DatePickerTarget.Production
                    showDatePicker = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(rows, key = { it.id }) { row ->
                    val expiryDate = row.days?.let { productionDate.plus(DatePeriod(days = it)) }

                    ExpiryRowItem(
                        days = row.days,
                        expiryDate = expiryDate,
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
                        },
                        onDelete = {
                            rows = rows.filter { it.id != row.id }
                        }
                    )
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
private fun ProductionDateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    onShowDatePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(date) { mutableStateOf(date.toString()) }
    var isError by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            isError = false
            try {
                onDateChange(LocalDate.parse(it))
            } catch (_: Exception) {
                isError = true
            }
        },
        label = { Text("生产日期") },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        trailingIcon = {
            IconButton(onClick = onShowDatePicker) {
                CalendarIcon(color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun ExpiryRowItem(
    days: Int?,
    expiryDate: LocalDate?,
    onDaysChange: (Int?) -> Unit,
    onExpiryDateChange: (LocalDate) -> Unit,
    onShowDatePicker: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var daysText by remember(days) { mutableStateOf(days?.toString() ?: "") }
    var dateText by remember(expiryDate) { mutableStateOf(expiryDate?.toString() ?: "") }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
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
        },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = daysText,
                onValueChange = { newValue ->
                    daysText = newValue.filter { it.isDigit() }
                    onDaysChange(daysText.toIntOrNull())
                },
                label = { Text("天数") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.weight(1f)
            )

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
                    IconButton(onClick = onShowDatePicker) {
                        CalendarIcon(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}

// region Icons

@Composable
private fun BackArrowIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(modifier = modifier.size(24.dp)) {
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
    Canvas(modifier = modifier.size(24.dp)) {
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
    Canvas(modifier = modifier.size(24.dp)) {
        val strokeWidth = 1.5f.dp.toPx()
        val pad = 3.dp.toPx()
        val topY = pad + 4.dp.toPx()
        val bottomY = size.height - pad
        val leftX = pad
        val rightX = size.width - pad

        // 外框
        drawLine(color, Offset(leftX, topY), Offset(rightX, topY), strokeWidth)
        drawLine(color, Offset(leftX, topY), Offset(leftX, bottomY), strokeWidth)
        drawLine(color, Offset(rightX, topY), Offset(rightX, bottomY), strokeWidth)
        drawLine(color, Offset(leftX, bottomY), Offset(rightX, bottomY), strokeWidth)

        // 顶部挂环
        val h1 = size.width * 0.3f
        val h2 = size.width * 0.7f
        drawLine(color, Offset(h1, pad), Offset(h1, topY), strokeWidth)
        drawLine(color, Offset(h2, pad), Offset(h2, topY), strokeWidth)
    }
}

// endregion

// region Helpers

private fun LocalDate.toEpochMillis(): Long =
    this.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

private fun Long.toLocalDate(): LocalDate =
    Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date

// endregion