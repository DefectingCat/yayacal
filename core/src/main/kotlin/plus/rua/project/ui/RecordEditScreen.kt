package plus.rua.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.DateRecorderRepository
import plus.rua.project.RecordEditUiState
import plus.rua.project.RecordEditViewModel
import kotlin.time.Instant

/**
 * 记录编辑页面，用于新建或修改一条日期记录的信息。
 *
 * 两种入口：
 * 1. 新建：[photoPath] 非空（来自相机/编辑器），表单初始为空
 * 2. 编辑：[recordId] 非空（来自详情页），预填已有记录，photoPath 从记录读取
 *
 * @param onBack 返回回调（保存或取消后触发）
 * @param photoPath 新建模式下的最终照片文件绝对路径；编辑模式为 null
 * @param recordId 编辑模式下的已有记录 ID；新建模式为 null
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(
    onBack: () -> Unit,
    photoPath: String?,
    recordId: Long?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val viewModel: RecordEditViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RecordEditViewModel(
                    repository = DateRecorderRepository.fromContext(context),
                    photoPath = photoPath,
                    recordId = recordId
                )
            }
        }
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.finished) {
        LaunchedEffect(Unit) { onBack() }
    }

    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (recordId != null) "编辑记录" else "新建记录",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            RecordEditForm(
                state = state,
                onTitleChange = viewModel::onTitleChange,
                onNoteChange = viewModel::onNoteChange,
                onShootDateChange = viewModel::onShootDateChange,
                onLinkedDateChange = viewModel::onLinkedDateChange,
                onClearLinkedDate = viewModel::onClearLinkedDate,
                onSave = viewModel::save,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordEditForm(
    state: RecordEditUiState,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onShootDateChange: (LocalDate) -> Unit,
    onLinkedDateChange: (LocalDate) -> Unit,
    onClearLinkedDate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showShootDatePicker by remember { mutableStateOf(false) }
    var showLinkedDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 照片预览
        state.photoUri?.let { uri ->
            AsyncImage(
                uri = uri,
                contentDescription = "记录照片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("record_edit_photo")
            )
        }

        // 标题
        OutlinedTextField(
            value = state.title,
            onValueChange = onTitleChange,
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("record_edit_title")
        )

        // 备注
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text("备注") },
            minLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("record_edit_note")
        )

        // 拍摄日期
        DatePickerField(
            label = "拍摄日期",
            date = state.shootDate,
            onClick = { showShootDatePicker = true },
            modifier = Modifier.testTag("record_edit_shoot_date")
        )

        // 关联日期（可空）
        DatePickerField(
            label = "关联日期",
            date = state.linkedDate,
            placeholder = "不关联",
            onClick = { showLinkedDatePicker = true },
            onClear = onClearLinkedDate,
            modifier = Modifier.testTag("record_edit_linked_date")
        )

        // 保存按钮
        Button(
            onClick = onSave,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("record_edit_save")
        ) {
            Text("保存")
        }
    }

    if (showShootDatePicker) {
        DatePickerModal(
            initialDate = state.shootDate,
            onConfirm = {
                onShootDateChange(it)
                showShootDatePicker = false
            },
            onDismiss = { showShootDatePicker = false }
        )
    }
    if (showLinkedDatePicker) {
        DatePickerModal(
            initialDate = state.linkedDate ?: state.shootDate,
            onConfirm = {
                onLinkedDateChange(it)
                showLinkedDatePicker = false
            },
            onDismiss = { showLinkedDatePicker = false }
        )
    }
}

@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate?,
    placeholder: String = "",
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = date?.let { formatLocalDate(it) } ?: placeholder,
            modifier = Modifier.fillMaxWidth()
        )
        if (onClear != null && date != null) {
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(
    initialDate: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = initialDate.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onConfirm(
                            Instant.fromEpochMilliseconds(millis)
                                .toLocalDateTime(TimeZone.currentSystemDefault())
                                .date
                        )
                    }
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun formatLocalDate(date: LocalDate): String {
    return "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}
