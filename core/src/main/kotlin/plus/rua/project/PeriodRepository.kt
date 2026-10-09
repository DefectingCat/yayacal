package plus.rua.project

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

/** 最近一次同步的结果。 */
sealed interface PeriodSyncStatus {
    /** 本次进程尚未同步。 */
    data object Idle : PeriodSyncStatus

    data object Syncing : PeriodSyncStatus

    data object Synced : PeriodSyncStatus

    data class Failed(
        val message: String,
    ) : PeriodSyncStatus

    /** 后端没有经期接口，需要先升级后端。 */
    data object Outdated : PeriodSyncStatus

    /** 后端拒绝了提交的文档；待同步操作保留，由用户决定是否放弃。 */
    data class Rejected(
        val message: String,
    ) : PeriodSyncStatus
}

/**
 * @property document 待同步操作重放到服务器快照后的文档，界面以此为准
 * @property pendingCount 尚未推送到服务器的操作数
 */
data class PeriodState(
    val document: PeriodDocument,
    val pendingCount: Int,
    val sync: PeriodSyncStatus,
)

/**
 * 经期文档的本机缓存与同步。
 *
 * 本机只保存服务器快照和待同步操作；同步时拉取最新文档、重放操作后以修订号提交，
 * 遇到 409 重新拉取再重放。同一时间只运行一次同步，期间新的请求合并为结束后的一次补跑。
 */
class PeriodRepository(
    private val storage: PeriodSyncStorage,
    private val serverUrl: () -> String,
    private val apiFactory: (String) -> PeriodApi,
    scope: CoroutineScope,
) {
    private val lock = Any()
    private var url = serverUrl()
    private var local = storage.load(url)
    private var cachedApi: Pair<String, PeriodApi>? = null
    private val _state = MutableStateFlow(PeriodState(view(local), local.pending.size, PeriodSyncStatus.Idle))
    val state: StateFlow<PeriodState> = _state.asStateFlow()
    private val requests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (request in requests) runSync()
        }
    }

    /** 页面回到前台时调用：服务地址变化则切换到该地址的本机状态，然后同步。 */
    fun refresh() {
        synchronized(lock) {
            val current = serverUrl()
            if (current != url) {
                url = current
                local = storage.load(url)
                publish(PeriodSyncStatus.Idle)
            }
        }
        requestSync()
    }

    /**
     * 记录一次修改并触发同步。
     *
     * @return 修改会违反文档规则时返回中文原因且不保存；没有实际变化时不产生待同步操作
     */
    fun submit(op: PeriodOp): String? {
        synchronized(lock) {
            val current = view(local)
            val next = op.applyTo(current)
            next.violation()?.let { return it }
            if (next.sameContentAs(current)) return null
            local = local.copy(pending = local.pending + PendingPeriodOp(local.nextSeq, op), nextSeq = local.nextSeq + 1)
            storage.save(url, local)
            publish(_state.value.sync)
        }
        requestSync()
        return null
    }

    /** 放弃全部未同步的修改，回到服务器快照。 */
    fun discardPending() {
        synchronized(lock) {
            local = local.copy(pending = emptyList())
            storage.save(url, local)
            publish(PeriodSyncStatus.Idle)
        }
        requestSync()
    }

    fun requestSync() {
        requests.trySend(Unit)
    }

    private suspend fun runSync() {
        val (syncUrl, api) = synchronized(lock) { url to apiFor(url) }
        synchronized(lock) { if (url == syncUrl) publish(PeriodSyncStatus.Syncing) }
        val status =
            try {
                pushAndPull(syncUrl, api)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PeriodApiException) {
                when (e.status) {
                    404 -> PeriodSyncStatus.Outdated
                    400 -> PeriodSyncStatus.Rejected(e.message.orEmpty())
                    else -> PeriodSyncStatus.Failed(e.message.orEmpty())
                }
            } catch (e: IOException) {
                PeriodSyncStatus.Failed(e.message ?: "同步失败")
            } catch (e: Exception) {
                // org.json 解析异常等：保留待同步操作，提示失败
                PeriodSyncStatus.Failed("同步失败：${e.message.orEmpty()}")
            }
        synchronized(lock) { if (url == syncUrl) publish(status) }
    }

    private suspend fun pushAndPull(
        syncUrl: String,
        api: PeriodApi,
    ): PeriodSyncStatus {
        repeat(MAX_ATTEMPTS) {
            val server = api.fetch()
            val pending = synchronized(lock) { if (url == syncUrl) local.pending else return PeriodSyncStatus.Idle }
            if (pending.isEmpty()) {
                commit(syncUrl, server, upToSeq = 0)
                return PeriodSyncStatus.Synced
            }
            val next = server.replay(pending.map { it.op })
            // 重放后没有变化：上次提交已成功但响应丢失，或操作均已被别处覆盖
            val saved =
                if (next.sameContentAs(server)) {
                    server
                } else {
                    try {
                        api.replace(next)
                    } catch (e: PeriodApiException) {
                        if (e.status == 409) return@repeat
                        throw e
                    }
                }
            commit(syncUrl, saved, upToSeq = pending.last().seq)
            return PeriodSyncStatus.Synced
        }
        return PeriodSyncStatus.Failed("其他设备正在频繁修改，请稍后重试")
    }

    /** 保存快照并清除序号不超过 [upToSeq] 的操作；请求期间新增的操作保留。 */
    private fun commit(
        syncUrl: String,
        snapshot: PeriodDocument,
        upToSeq: Long,
    ) {
        synchronized(lock) {
            if (url != syncUrl) return
            local = local.copy(snapshot = snapshot, pending = local.pending.filter { it.seq > upToSeq })
            storage.save(url, local)
            publish(_state.value.sync)
        }
    }

    private fun apiFor(url: String): PeriodApi = cachedApi?.takeIf { it.first == url }?.second
        ?: apiFactory(url).also { cachedApi = url to it }

    private fun publish(sync: PeriodSyncStatus) {
        _state.value = PeriodState(view(local), local.pending.size, sync)
    }

    private fun view(state: PeriodLocalState): PeriodDocument = (state.snapshot ?: PeriodDocument()).replay(state.pending.map { it.op })

    companion object {
        private const val MAX_ATTEMPTS = 3

        @Volatile
        @Suppress("ObjectPropertyName") // Android singleton 惯例
        private var INSTANCE: PeriodRepository? = null

        /** 进程内单例，经期相关的多个页面共用同一份状态。 */
        fun fromContext(context: Context): PeriodRepository = INSTANCE ?: synchronized(this) {
            INSTANCE ?: run {
                val app = context.applicationContext
                PeriodRepository(
                    storage = PeriodSyncStorage.fromContext(app),
                    serverUrl = { MomentsConnection.url(app) },
                    apiFactory = ::HttpPeriodApi,
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                )
            }.also { INSTANCE = it }
        }
    }
}
