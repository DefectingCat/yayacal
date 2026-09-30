package plus.rua.project

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 朋友圈全部互动消息页面 UI 状态。
 *
 * @param notifications 互动消息列表（按时间降序排列）
 */
data class MomentsNotificationsUiState(
    val notifications: List<MomentNotification> = emptyList(),
)

/**
 * 朋友圈全部互动消息 ViewModel，负责消息列表的加载、刷新、单条删除与全量清空。
 *
 * @param storage 互动消息存储仓库
 */
class MomentsNotificationsViewModel(
    private val storage: MomentsNotificationStorage,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MomentsNotificationsUiState(
                notifications = storage.getNotifications(),
            ),
        )
    val uiState: StateFlow<MomentsNotificationsUiState> = _uiState.asStateFlow()

    /**
     * 从持久化存储中刷新消息列表。
     */
    fun refresh() {
        _uiState.update {
            it.copy(notifications = storage.getNotifications())
        }
    }

    /**
     * 删除指定 ID 的互动消息。
     *
     * @param id 互动消息唯一标识符
     */
    fun deleteNotification(id: String) {
        storage.deleteNotification(id)
        refresh()
    }

    /**
     * 清空全部互动消息。
     */
    fun clearAll() {
        storage.clearNotifications()
        refresh()
    }
}
