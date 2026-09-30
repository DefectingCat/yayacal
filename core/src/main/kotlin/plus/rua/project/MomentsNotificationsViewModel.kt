package plus.rua.project

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 当前账号互动消息及分页/错误状态。 */
data class MomentsNotificationsUiState(
    val notifications: List<MomentNotification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val unreadCount: Int = 0,
)

/** 只读取真实服务端消息，当前页读取成功后标记该页已读。 */
class MomentsNotificationsViewModel(
    private val storage: MomentsStorage,
    private val repository: MomentsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MomentsNotificationsUiState())
    val uiState = _uiState.asStateFlow()
    private var job: Job? = null
    private var account: String? = null
    private var generation = 0

    fun refresh(more: Boolean = false) {
        val actor = MomentAccount.findById(storage.getCurrentAccountId()).id
        if (account != actor) {
            account = actor
            _uiState.value = MomentsNotificationsUiState()
        }
        val cursor = if (more) _uiState.value.nextCursor ?: return else null
        job?.cancel()
        val version = ++generation
        _uiState.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val page = repository.notifications(actor, cursor)
                if (version == generation && storage.getCurrentAccountId() == actor) {
                    _uiState.update {
                        it.copy(
                            notifications = if (more) (it.notifications + page.items).distinctBy { n -> n.id } else page.items,
                            nextCursor = page.nextCursor,
                            unreadCount = page.unreadCount,
                            isLoading = false,
                        )
                    }
                    repository.readNotifications(actor, page.items.filter { !it.isRead }.map { it.id })
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation) _uiState.update { it.copy(error = e.message ?: "消息加载失败", isLoading = false) }
            }
        }
    }
    fun deleteNotification(id: String) = delete(id)
    fun clearAll() = delete(null)
    private fun delete(id: String?) {
        val actor = MomentAccount.findById(storage.getCurrentAccountId()).id
        viewModelScope.launch {
            try {
                repository.deleteNotification(actor, id)
                if (actor == storage.getCurrentAccountId()) refresh()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (actor == storage.getCurrentAccountId()) _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    companion object {
        fun fromContext(context: Context) = MomentsNotificationsViewModel(MomentsStorage.fromContext(context), MomentsConnection.repository(context.applicationContext))
    }
}
