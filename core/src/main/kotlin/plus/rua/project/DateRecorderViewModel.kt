package plus.rua.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 日期记录器列表的排序方式。
 *
 * @param field 排序字段
 * @param ascending 是否升序；false 表示降序
 */
data class RecordSortOrder(
    val field: RecordSortField,
    val ascending: Boolean
) {
    companion object {
        /** 默认按拍摄日期降序（最新的在前） */
        val DEFAULT = RecordSortOrder(RecordSortField.SHOOT_DATE, ascending = false)

        /**
         * 计算用户在排序菜单中点击某字段后的下一个排序方式。
         *
         * 点击当前已选字段时翻转方向，点击其他字段时切换字段并保持方向不变。
         *
         * @param current 当前排序方式
         * @param field 用户点击的排序字段
         */
        fun nextAfter(current: RecordSortOrder, field: RecordSortField): RecordSortOrder =
            if (current.field == field) {
                current.copy(ascending = !current.ascending)
            } else {
                RecordSortOrder(field, current.ascending)
            }
    }
}

/** 排序字段选项。 */
enum class RecordSortField {
    /** 拍摄日期 */
    SHOOT_DATE,

    /** 关联日期（无关联日期的记录排末尾） */
    LINKED_DATE,

    /** 记录创建时间 */
    CREATED_AT
}

/**
 * 日期记录器主界面 UI 状态。
 *
 * @param records 排序后的记录列表
 * @param sortOrder 当前排序方式
 * @param isLoading 首次加载中
 * @param selectionMode 是否处于多选模式
 * @param selectedIds 当前选中的记录 ID 集合
 */
data class DateRecorderUiState(
    val records: List<DateRecord> = emptyList(),
    val sortOrder: RecordSortOrder = RecordSortOrder.DEFAULT,
    val isLoading: Boolean = true,
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet()
) {
    /** 是否已选中全部记录（供"全选/取消全选"切换显示） */
    val allSelected: Boolean get() = records.isNotEmpty() && selectedIds.size == records.size
}

/**
 * 日期记录器列表的 ViewModel。
 *
 * 订阅 [DateRecorderRepository] 的全部记录 Flow，并按用户选择的排序方式排序后暴露。
 *
 * @param repository 数据仓库
 */
class DateRecorderViewModel(
    private val repository: DateRecorderRepository
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(RecordSortOrder.DEFAULT)
    val sortOrder: StateFlow<RecordSortOrder> = _sortOrder.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * 聚合的 UI 状态：仓库记录 Flow + 排序 Flow + 多选 Flow 合并、排序。
     */
    val uiState: StateFlow<DateRecorderUiState> = run {
        kotlinx.coroutines.flow.combine(
            repository.observeAll(),
            _sortOrder,
            _selectionMode,
            _selectedIds
        ) { records, order, selectionMode, selectedIds ->
            DateRecorderUiState(
                records = sortRecords(records, order),
                sortOrder = order,
                isLoading = false,
                selectionMode = selectionMode,
                selectedIds = selectedIds
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DateRecorderUiState()
        )
    }

    /**
     * 切换排序方式。
     *
     * @param order 新的排序方式
     */
    fun setSortOrder(order: RecordSortOrder) {
        _sortOrder.value = order
    }

    /**
     * 进入/退出多选模式。进入时清空已有选择。
     */
    fun toggleSelectionMode() {
        _selectionMode.value = !_selectionMode.value
        _selectedIds.value = emptySet()
    }

    /**
     * 开启多选模式并选中指定的记录 ID（响应长按操作）。
     *
     * @param id 长按选中的记录 ID
     */
    fun startSelectionModeWith(id: Long) {
        _selectionMode.value = true
        _selectedIds.value = setOf(id)
    }

    /**
     * 切换某条记录的选中状态。
     */
    fun toggleSelection(id: Long) {
        _selectedIds.value = _selectedIds.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    /**
     * 全选 / 取消全选。
     */
    fun toggleSelectAll() {
        val records = uiState.value.records
        _selectedIds.value = if (uiState.value.allSelected) {
            emptySet()
        } else {
            records.map { it.id }.toSet()
        }
    }

    /**
     * 删除当前选中的所有记录（含照片文件），完成后退出多选模式。
     */
    fun deleteSelected() {
        val selectedIds = _selectedIds.value
        if (selectedIds.isEmpty()) return
        val toDelete = uiState.value.records.filter { it.id in selectedIds }
        viewModelScope.launch {
            repository.deleteWithPhotos(toDelete)
            _selectionMode.value = false
            _selectedIds.value = emptySet()
        }
    }

    /**
     * 按 ID 批量删除记录（含照片文件）。
     *
     * @param records 待删除记录列表
     */
    fun deleteRecords(records: List<DateRecord>) {
        viewModelScope.launch {
            repository.deleteWithPhotos(records)
        }
    }

    private fun sortRecords(
        records: List<DateRecord>,
        order: RecordSortOrder
    ): List<DateRecord> = sortDateRecords(records, order)

    companion object {
        /**
         * 按指定方式排序记录列表。抽为 companion 静态方法以便单元测试。
         */
        fun sortDateRecords(
            records: List<DateRecord>,
            order: RecordSortOrder
        ): List<DateRecord> {
            val comparator: Comparator<DateRecord> = when (order.field) {
                RecordSortField.SHOOT_DATE -> compareBy { it.shootDate }
                RecordSortField.LINKED_DATE -> compareBy(nullsLast()) { it.linkedDate }
                RecordSortField.CREATED_AT -> compareBy { it.createdAt }
            }
            // id 兜底随主排序一起反转，保证降序时同值记录也是最新（id 最大）的在前
            val ordered = comparator.then(compareBy { it.id })
            return records.sortedWith(if (order.ascending) ordered else ordered.reversed())
        }
    }
}
