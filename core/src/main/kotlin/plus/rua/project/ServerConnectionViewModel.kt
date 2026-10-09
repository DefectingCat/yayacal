package plus.rua.project

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 工具页服务器卡片的状态。
 *
 * @property url 已保存的服务器地址，朋友圈与经期记录共用
 * @property status 已保存地址的连接状态
 * @property draftStatus 编辑中地址的「测试连接」结果；未测试时为 null
 * @property draftError 编辑中地址的格式错误
 * @property savedCount 每次保存成功加 1，界面据此播放保存完成动画
 */
data class ServerConnectionUiState(
    val url: String,
    val defaultUrl: String,
    val presets: List<ServerPreset>,
    val status: ServerStatus = ServerStatus.Checking,
    val draftStatus: ServerStatus? = null,
    val draftError: String? = null,
    val savedCount: Int = 0,
)

/** 读写服务器地址并检查连接；保存后朋友圈和经期记录在下次进入或回到前台时使用新地址。 */
class ServerConnectionViewModel internal constructor(
    private val settings: MomentsConnectionSettings,
    private val probe: ServerProbe,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(ServerConnectionUiState(url = settings.url(), defaultUrl = settings.defaultUrl, presets = settings.presets()))
    val uiState: StateFlow<ServerConnectionUiState> = _uiState.asStateFlow()
    private var statusJob: Job? = null
    private var draftJob: Job? = null

    /** 重新读取已保存地址并检查连接，页面回到前台和授权完成后调用。 */
    fun refresh() {
        val url = settings.url()
        _uiState.update { it.copy(url = url, status = ServerStatus.Checking) }
        statusJob?.cancel()
        statusJob =
            viewModelScope.launch {
                val status = probe.probe(url)
                _uiState.update { if (it.url == url) it.copy(status = status) else it }
            }
    }

    /** 校验并检查编辑中的地址，不保存。 */
    fun test(draft: String) {
        val url =
            try {
                settings.normalize(draft)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(draftError = e.message, draftStatus = null) }
                return
            }
        _uiState.update { it.copy(draftError = null, draftStatus = ServerStatus.Checking) }
        draftJob?.cancel()
        draftJob =
            viewModelScope.launch {
                val status = probe.probe(url)
                _uiState.update { it.copy(draftStatus = status) }
            }
    }

    /**
     * 保存编辑中的地址。
     *
     * @return 保存成功返回 true；格式不符合要求时返回 false 并在 [ServerConnectionUiState.draftError] 给出原因
     */
    fun save(draft: String): Boolean {
        val saved =
            try {
                settings.save(draft)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(draftError = e.message, draftStatus = null) }
                return false
            }
        draftJob?.cancel()
        val tested = _uiState.value.draftStatus?.takeIf { it !is ServerStatus.Checking && settings.normalize(draft) == saved }
        _uiState.update { it.copy(url = saved, draftError = null, draftStatus = null, savedCount = it.savedCount + 1) }
        if (tested != null) {
            statusJob?.cancel()
            _uiState.update { it.copy(status = tested) }
        } else {
            refresh()
        }
        return true
    }

    /** 编辑中的地址变化或关闭编辑时清除测试结果。 */
    fun clearDraft() {
        draftJob?.cancel()
        _uiState.update { it.copy(draftStatus = null, draftError = null) }
    }

    companion object {
        fun fromContext(context: Context): ServerConnectionViewModel {
            val app = context.applicationContext
            return ServerConnectionViewModel(MomentsConnection.settings(app), HttpServerProbe(app))
        }
    }
}
