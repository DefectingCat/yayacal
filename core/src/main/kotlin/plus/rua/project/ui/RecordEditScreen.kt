package plus.rua.project.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.datetime.DayOfWeek
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.DateRecorderRepository
import plus.rua.project.RecordEditUiState
import plus.rua.project.RecordEditViewModel

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
                        text = if (recordId != null) "编辑记录" else "新建记录",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.canSave
                    ) {
                        Text(
                            text = "保存",
                            fontWeight = FontWeight.Bold,
                            color = if (state.canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Button(
                        onClick = viewModel::save,
                        enabled = state.canSave,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("record_edit_save")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "保存记录",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

/**
 * 记录编辑表单区。
 *
 * @param state UI 状态
 * @param onTitleChange 标题变更回调
 * @param onNoteChange 笔记变更回调
 * @param onShootDateChange 拍摄日期变更回调
 * @param onLinkedDateChange 关联日期变更回调
 * @param onClearLinkedDate 清除关联日期回调
 * @param modifier 布局修饰符
 */
@Composable
private fun RecordEditForm(
    state: RecordEditUiState,
    onTitleChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onShootDateChange: (LocalDate) -> Unit,
    onLinkedDateChange: (LocalDate) -> Unit,
    onClearLinkedDate: () -> Unit,
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
        // 照片预览卡片
        state.photoUri?.let { uri ->
            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("record_edit_photo")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 260.dp)
                ) {
                    AsyncImage(
                        uri = uri,
                        contentDescription = "记录照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "记录照片",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 区域一：基本信息卡片
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "基本信息",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 标题输入框
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    label = { Text("记录标题") },
                    placeholder = { Text("请输入记录标题...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Title,
                            contentDescription = null
                        )
                    },
                    trailingIcon = {
                        if (state.title.isNotEmpty()) {
                            IconButton(onClick = { onTitleChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "清除标题")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_edit_title")
                )

                // 备注输入框
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChange,
                    label = { Text("记录笔记 / 备注") },
                    placeholder = { Text("记录当天的所思所想、天气或难忘瞬间...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Notes,
                            contentDescription = null
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_edit_note")
                )
            }
        }

        // 区域二：日期设定卡片
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "日期设定",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 拍摄日期选择条目
                DatePickerTile(
                    icon = Icons.Outlined.CalendarToday,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = "拍摄日期",
                    valueText = formatChineseDateWithWeek(state.shootDate),
                    onClick = { showShootDatePicker = true },
                    modifier = Modifier.testTag("record_edit_shoot_date")
                )

                // 关联日期选择条目
                val hasLinked = state.linkedDate != null
                DatePickerTile(
                    icon = if (hasLinked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    iconTint = if (hasLinked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "关联日历",
                    valueText = state.linkedDate?.let { formatChineseDateWithWeek(it) } ?: "独立记录（未关联日历）",
                    onClick = { showLinkedDatePicker = true },
                    onClear = if (hasLinked) onClearLinkedDate else null,
                    modifier = Modifier.testTag("record_edit_linked_date")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
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

/**
 * 交互式日期选择条目。
 *
 * @param icon 图标
 * @param iconTint 图标色值
 * @param label 标签名称
 * @param valueText 值显示文本
 * @param onClick 点击回调（打开 DatePicker）
 * @param onClear 清除值回调（可选）
 * @param modifier 布局修饰符
 */
@Composable
private fun DatePickerTile(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    valueText: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (onClear != null) {
                TextButton(onClick = onClear) {
                    Text(
                        text = "清除",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = "选择日期",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 原生 Material 3 DatePicker 弹窗。
 *
 * @param initialDate 初始选中日期
 * @param onConfirm 确认选择日期回调
 * @param onDismiss 关闭弹窗回调
 */
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

private fun formatChineseDateWithWeek(date: LocalDate): String {
    val weekStr = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "星期一"
        DayOfWeek.TUESDAY -> "星期二"
        DayOfWeek.WEDNESDAY -> "星期三"
        DayOfWeek.THURSDAY -> "星期四"
        DayOfWeek.FRIDAY -> "星期五"
        DayOfWeek.SATURDAY -> "星期六"
        DayOfWeek.SUNDAY -> "星期日"
    }
    return "${date.year}年${date.month.number}月${date.day}日 $weekStr"
}
