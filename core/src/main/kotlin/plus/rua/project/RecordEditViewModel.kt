package plus.rua.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 记录编辑页面 UI 状态。
 *
 * @param loading 加载已有记录中（仅编辑模式）
 * @param title 标题
 * @param note 备注
 * @param shootDate 拍摄日期
 * @param linkedDate 关联日期（可空）
 * @param photoUri 用于 AsyncImage 显示的 URI 字符串（file:// 绝对路径或 content://）
 * @param photoAbsolutePath 照片绝对路径，保存时持久化相对路径
 * @param canSave 是否可保存（标题非空且照片就绪）
 * @param finished 保存完成，UI 据此触发返回
 * @param isExistingRecord 是否为编辑已有记录模式
 */
data class RecordEditUiState(
    val loading: Boolean = true,
    val title: String = "",
    val note: String = "",
    val shootDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val linkedDate: LocalDate? = null,
    val photoUri: String? = null,
    val photoAbsolutePath: String? = null,
    val canSave: Boolean = false,
    val finished: Boolean = false,
    val isExistingRecord: Boolean = false
)

/**
 * 记录编辑页面 ViewModel，处理新建与编辑两种模式。
 *
 * @param repository 数据仓库
 * @param photoPath 新建模式下的照片绝对路径，或编辑模式下重新编辑后的照片绝对路径（可空）
 * @param recordId 编辑模式下的已有记录 ID，新建模式为 null
 */
class RecordEditViewModel(
    private val repository: DateRecorderRepository,
    private val photoPath: String?,
    private val recordId: Long?
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordEditUiState())
    val uiState: StateFlow<RecordEditUiState> = _uiState.asStateFlow()

    /** 用户是否手动编辑过标题；为 false 时标题随拍摄日期联动 */
    private var titleManuallyEdited = false
    private var existingCreatedAt: Instant? = null
    private var originalPhotoRelativePath: String? = null
    init {
        if (recordId != null) {
            loadExistingRecord(recordId)
        } else {
            initNewRecord()
        }
    }

    private fun initNewRecord() {
        requireNotNull(photoPath) { "新建模式必须提供 photoPath" }
        val file = File(photoPath)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        _uiState.value = RecordEditUiState(
            loading = false,
            title = formatLocalDate(today),
            shootDate = today,
            photoUri = "file://${file.absolutePath}",
            photoAbsolutePath = file.absolutePath,
            canSave = true,
            isExistingRecord = false
        )
    }

    private fun loadExistingRecord(id: Long) {
        viewModelScope.launch {
            val record = repository.observeById(id).first()
            if (record != null) {
                existingCreatedAt = record.createdAt
                originalPhotoRelativePath = record.photoPath
                val absFile = if (photoPath != null) {
                    File(photoPath)
                } else {
                    repository.absoluteFileOf(record.photoPath)
                }
                titleManuallyEdited = true
                _uiState.value = RecordEditUiState(
                    loading = false,
                    title = record.title,
                    note = record.note,
                    shootDate = record.shootDate,
                    linkedDate = record.linkedDate,
                    photoUri = "file://${absFile.absolutePath}",
                    photoAbsolutePath = absFile.absolutePath,
                    canSave = true,
                    isExistingRecord = true
                )
            } else {
                // 记录不存在（已被删除），直接结束
                _uiState.update { it.copy(loading = false, finished = true) }
            }
        }
    }

    fun onTitleChange(value: String) {
        titleManuallyEdited = true
        _uiState.update { it.copy(title = value, canSave = value.isNotBlank() && it.photoAbsolutePath != null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value) }
    }

    fun onShootDateChange(date: LocalDate) {
        _uiState.update {
            if (titleManuallyEdited) {
                it.copy(shootDate = date)
            } else {
                it.copy(shootDate = date, title = formatLocalDate(date))
            }
        }
    }

    fun onLinkedDateChange(date: LocalDate) {
        _uiState.update { it.copy(linkedDate = date) }
    }

    fun onClearLinkedDate() {
        _uiState.update { it.copy(linkedDate = null) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave || state.photoAbsolutePath == null) return
        viewModelScope.launch {
            val relPath = repository.relativePathOf(File(state.photoAbsolutePath))
            if (state.isExistingRecord && recordId != null) {
                repository.update(
                    DateRecord(
                        id = recordId,
                        title = state.title,
                        note = state.note,
                        shootDate = state.shootDate,
                        linkedDate = state.linkedDate,
                        photoPath = relPath,
                        createdAt = existingCreatedAt ?: Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    )
                )
                originalPhotoRelativePath?.let { oldPath ->
                    if (oldPath != relPath) {
                        repository.deletePhotoFile(oldPath)
                    }
                }
            } else {
                repository.insert(
                    DateRecord(
                        id = 0,
                        title = state.title,
                        note = state.note,
                        shootDate = state.shootDate,
                        linkedDate = state.linkedDate,
                        photoPath = relPath,
                        createdAt = Instant.fromEpochMilliseconds(System.currentTimeMillis())
                    )
                )
            }
            _uiState.update { it.copy(finished = true) }
        }
    }
}

/**
 * 将日期格式化为 `yyyy-MM-dd`，用于新建记录的默认标题和编辑页的日期显示。
 *
 * @param date 待格式化的日期
 */
internal fun formatLocalDate(date: LocalDate): String {
    return "${date.year}-${date.month.number.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}
