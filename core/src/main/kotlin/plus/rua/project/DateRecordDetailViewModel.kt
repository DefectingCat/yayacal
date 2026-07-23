package plus.rua.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 记录详情页 UI 状态。
 *
 * @param loading 加载中
 * @param record 当前记录；不存在时为 null
 * @param photoUri 用于 AsyncImage 显示的 URI 字符串
 * @param deleted 删除已完成，UI 据此触发返回
 */
data class DateRecordDetailUiState(
    val loading: Boolean = true,
    val record: DateRecord? = null,
    val photoUri: String? = null,
    val deleted: Boolean = false
)

/**
 * 记录详情页 ViewModel，订阅单条记录并支持删除。
 *
 * @param repository 数据仓库
 * @param recordId 记录 ID
 */
class DateRecordDetailViewModel(
    private val repository: DateRecorderRepository,
    private val recordId: Long
) : ViewModel() {

    private val _uiState = MutableStateFlow(DateRecordDetailUiState())
    val uiState: StateFlow<DateRecordDetailUiState> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            repository.observeById(recordId).collect { record ->
                if (record != null) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            record = record,
                            photoUri = "file://${repository.absoluteFileOf(record.photoPath).absolutePath}"
                        )
                    }
                } else {
                    _uiState.update { it.copy(loading = false, record = null) }
                }
            }
        }
    }

    /** 返回当前照片绝对路径，供"编辑照片"跳转使用。 */
    fun currentPhotoPath(): String? = _uiState.value.record?.let {
        repository.absoluteFileOf(it.photoPath).absolutePath
    }

    /** 删除当前记录及其照片文件。 */
    fun delete() {
        val record = _uiState.value.record ?: return
        viewModelScope.launch {
            repository.deleteWithPhoto(record)
            _uiState.update { it.copy(deleted = true) }
        }
    }
}
