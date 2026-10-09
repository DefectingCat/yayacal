package plus.rua.project

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** 经期页面共用的界面状态；[forecast] 由 [document] 与 [today] 计算。 */
data class PeriodUiState(
    val today: LocalDate,
    val document: PeriodDocument,
    val forecast: PeriodForecast,
    val sync: PeriodSyncStatus,
    val pendingCount: Int,
)

/**
 * 经期首页、历史页、设置页共用的 ViewModel，修改都转成 [PeriodOp] 交给 [PeriodRepository]。
 *
 * @param clock 注入时钟，便于测试跨天与日期边界
 */
class PeriodViewModel(
    private val repository: PeriodRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {
    private val today = MutableStateFlow(clock.todayIn(timeZone))
    private val _message = MutableStateFlow<String?>(null)

    /** 一次性提示（例如修改不符合规则未保存），展示后调用 [dismissMessage]。 */
    val message: StateFlow<String?> = _message.asStateFlow()

    val uiState: StateFlow<PeriodUiState> =
        combine(repository.state, today, ::buildState)
            .stateIn(viewModelScope, SharingStarted.Eagerly, buildState(repository.state.value, today.value))

    /** 页面回到前台：刷新"今天"并同步。 */
    fun onResume() {
        today.value = clock.todayIn(timeZone)
        repository.refresh()
    }

    fun startPeriod(date: LocalDate) {
        val latestEnd = repository.state.value.document.ranges
            .lastOrNull()
            ?.end
        if (latestEnd != null && date <= latestEnd) {
            _message.value = "开始日期需晚于上次经期的结束日"
            return
        }
        submit(PeriodOp.StartPeriod(date))
    }

    fun endPeriod(date: LocalDate) = submit(PeriodOp.EndPeriod(date))

    fun setPeriodDay(
        date: LocalDate,
        isPeriod: Boolean,
    ) = submit(PeriodOp.SetPeriodDay(date, isPeriod, today.value))

    fun saveNote(
        date: LocalDate,
        mood: PeriodMood?,
        text: String,
    ) = submit(PeriodOp.SetNote(date, mood, text))

    fun updateRange(
        oldStart: LocalDate,
        start: LocalDate,
        end: LocalDate?,
    ) = submit(PeriodOp.UpdateRange(oldStart, start, end))

    fun deleteRange(start: LocalDate) = submit(PeriodOp.DeleteRange(start))

    fun updateSettings(settings: PeriodSettings) = submit(PeriodOp.UpdateSettings(settings))

    fun retrySync() = repository.requestSync()

    fun discardPending() = repository.discardPending()

    fun dismissMessage() {
        _message.value = null
    }

    private fun submit(op: PeriodOp) {
        repository.submit(op)?.let { _message.value = "未保存：$it" }
    }

    private fun buildState(
        state: PeriodState,
        day: LocalDate,
    ) = PeriodUiState(
        today = day,
        document = state.document,
        forecast = PeriodPredictor.forecast(state.document, day),
        sync = state.sync,
        pendingCount = state.pendingCount,
    )

    companion object {
        fun fromContext(context: Context): PeriodViewModel = PeriodViewModel(PeriodRepository.fromContext(context), Clock.System)
    }
}
