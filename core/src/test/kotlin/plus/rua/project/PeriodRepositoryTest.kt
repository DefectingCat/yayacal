package plus.rua.project

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PeriodRepositoryTest {
    private val today = LocalDate(2026, 10, 9)
    private val prefs = PeriodTestPrefs()
    private val storage = PeriodSyncStorage(prefs)
    private var url = "https://a.example"

    private fun repository(
        api: FakePeriodApi,
        scope: CoroutineScope,
    ) = PeriodRepository(storage, { url }, { api }, scope)

    // backgroundScope 的任务不参与 advanceUntilIdle 判断，这里改用前台调度器驱动同步协程
    private fun TestScope.repo(api: FakePeriodApi) = repository(api, CoroutineScope(StandardTestDispatcher(testScheduler) + Job()))

    @Test
    fun submit_offline_keepsPendingAndShowsReplayedDocument() = runTest {
        val api = FakePeriodApi().apply { failure = IOException("连接失败，请检查网络或服务地址") }
        val repository = repo(api)

        repository.submit(PeriodOp.StartPeriod(today))
        advanceUntilIdle()

        val state = repository.state.value
        assertEquals(listOf(PeriodRange(today, null)), state.document.ranges)
        assertEquals(1, state.pendingCount)
        assertEquals(PeriodSyncStatus.Failed("连接失败，请检查网络或服务地址"), state.sync)
        assertEquals(1, storage.load(url).pending.size)
    }

    @Test
    fun sync_success_putsReplayedDocumentAndClearsPending() = runTest {
        val api = FakePeriodApi()
        val repository = repo(api)

        repository.submit(PeriodOp.SetNote(today, PeriodMood.CALM, "热水袋"))
        advanceUntilIdle()

        assertEquals(1, api.server.revision)
        assertEquals(listOf(PeriodNote(today, PeriodMood.CALM, "热水袋")), api.server.notes)
        val state = repository.state.value
        assertEquals(PeriodSyncStatus.Synced, state.sync)
        assertEquals(0, state.pendingCount)
        assertEquals(api.server, storage.load(url).snapshot)
    }

    @Test
    fun sync_conflict_refetchesAndReplaysOnLatestDocument() = runTest {
        val api = FakePeriodApi()
        val repository = repo(api)
        api.beforeReplace = {
            // 另一台设备抢先提交了经期开始
            api.server = api.server.copy(revision = api.server.revision + 1, ranges = listOf(PeriodRange(LocalDate(2026, 10, 8), null)))
            api.beforeReplace = null
        }

        repository.submit(PeriodOp.SetNote(today, PeriodMood.TIRED, ""))
        advanceUntilIdle()

        assertEquals(2, api.replaceCalls)
        assertEquals(listOf(PeriodRange(LocalDate(2026, 10, 8), null)), api.server.ranges)
        assertEquals(listOf(PeriodNote(today, PeriodMood.TIRED, "")), api.server.notes)
        assertEquals(PeriodSyncStatus.Synced, repository.state.value.sync)
    }

    @Test
    fun sync_replayWithoutChange_clearsPendingWithoutPut() = runTest {
        // 上次提交已成功但响应丢失：服务器已包含本机操作
        val api = FakePeriodApi().apply { server = PeriodDocument(revision = 4, ranges = listOf(PeriodRange(today, null))) }
        storage.save(url, PeriodLocalState(PeriodDocument(revision = 3), listOf(PendingPeriodOp(1, PeriodOp.StartPeriod(today))), 2))
        val repository = repo(api)

        repository.requestSync()
        advanceUntilIdle()

        assertEquals(0, api.replaceCalls)
        assertEquals(0, repository.state.value.pendingCount)
        assertEquals(4, repository.state.value.document.revision)
    }

    @Test
    fun sync_oldServer_reportsOutdatedAndKeepsPending() = runTest {
        val api = FakePeriodApi().apply { failure = PeriodApiException(404, "内容不存在") }
        val repository = repo(api)

        repository.submit(PeriodOp.StartPeriod(today))
        advanceUntilIdle()

        assertEquals(PeriodSyncStatus.Outdated, repository.state.value.sync)
        assertEquals(1, repository.state.value.pendingCount)
    }

    @Test
    fun sync_rejected_keepsPendingUntilDiscarded() = runTest {
        val api = FakePeriodApi().apply { replaceFailure = PeriodApiException(400, "经期区间不能重叠或相邻") }
        val repository = repo(api)

        repository.submit(PeriodOp.StartPeriod(today))
        advanceUntilIdle()
        assertEquals(PeriodSyncStatus.Rejected("经期区间不能重叠或相邻"), repository.state.value.sync)
        assertEquals(1, repository.state.value.pendingCount)

        repository.discardPending()
        advanceUntilIdle()
        assertEquals(PeriodSyncStatus.Synced, repository.state.value.sync)
        assertEquals(emptyList(), repository.state.value.document.ranges)
    }

    @Test
    fun sync_opsSubmittedDuringRequest_areKeptAndPushedNext() = runTest {
        val api = FakePeriodApi()
        val gate = CompletableDeferred<Unit>()
        api.replaceGate = gate
        val repository = repo(api)

        repository.submit(PeriodOp.StartPeriod(today))
        advanceUntilIdle()
        repository.submit(PeriodOp.SetNote(today, PeriodMood.LOW, ""))
        assertEquals(2, repository.state.value.pendingCount)
        api.replaceGate = null
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, repository.state.value.pendingCount)
        assertEquals(listOf(PeriodRange(today, null)), api.server.ranges)
        assertEquals(listOf(PeriodNote(today, PeriodMood.LOW, "")), api.server.notes)
        assertEquals(2, api.server.revision)
    }

    @Test
    fun submit_invalidChange_returnsReasonWithoutPending() = runTest {
        val api = FakePeriodApi().apply { server = PeriodDocument(revision = 1, ranges = listOf(PeriodRange(LocalDate(2026, 9, 1), null))) }
        val repository = repo(api)
        repository.requestSync()
        advanceUntilIdle()

        val reason = repository.submit(PeriodOp.EndPeriod(today))

        assertEquals("单次经期不能超过 31 天", reason)
        assertEquals(0, repository.state.value.pendingCount)
    }

    @Test
    fun submit_noActualChange_doesNotQueue() = runTest {
        val repository = repo(FakePeriodApi())

        repository.submit(PeriodOp.DeleteRange(today))

        assertEquals(0, repository.state.value.pendingCount)
    }

    @Test
    fun refresh_serverUrlChanged_switchesToThatServersState() = runTest {
        val api = FakePeriodApi().apply { failure = IOException("离线") }
        val repository = repo(api)
        repository.submit(PeriodOp.StartPeriod(today))
        advanceUntilIdle()

        url = "https://b.example"
        repository.refresh()
        advanceUntilIdle()

        assertEquals(0, repository.state.value.pendingCount)
        assertEquals(emptyList(), repository.state.value.document.ranges)
        assertTrue(storage.load("https://a.example").pending.isNotEmpty())
    }
}

/** 模拟后端的修订号语义：base_revision 不一致返回 409。 */
internal class FakePeriodApi : PeriodApi {
    var server = PeriodDocument()
    var failure: Exception? = null
    var replaceFailure: Exception? = null
    var beforeReplace: (() -> Unit)? = null
    var replaceGate: CompletableDeferred<Unit>? = null
    var replaceCalls = 0

    override suspend fun fetch(): PeriodDocument {
        failure?.let { throw it }
        return server
    }

    override suspend fun replace(document: PeriodDocument): PeriodDocument {
        replaceCalls++
        replaceGate?.await()
        replaceFailure?.let { throw it }
        beforeReplace?.invoke()
        if (document.revision != server.revision) throw PeriodApiException(409, "经期记录已在其他设备更新")
        server = document.copy(revision = server.revision + 1)
        return server
    }
}
