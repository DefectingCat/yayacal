package plus.rua.project

import android.content.SharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MomentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val storage = MomentsStorage(MomentsVmTestInMemoryPrefs())
    private val repository = FakeMomentsRepository()

    @Before fun before() {
        Dispatchers.setMain(dispatcher)
        storage.saveCurrentAccountId("xiaobai")
    }

    @After fun after() {
        Dispatchers.resetMain()
    }

    @Test fun switchAccount_lateOldResponse_doesNotLeakPreviousAccount() = runTest(dispatcher) {
        val oldResponse = CompletableDeferred<Unit>()
        repository.load = { actor, _, _, _ ->
            if (actor == "xiaobai") withContext(NonCancellable) { oldResponse.await() }
            MomentsPage(listOf(MomentPost(id = actor, authorId = actor)))
        }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        runCurrent()
        vm.switchAccount(MomentAccount.ACCOUNT_XIAOJIMAO)
        assertTrue(vm.uiState.value.posts.isEmpty())
        runCurrent()
        oldResponse.complete(Unit)
        advanceUntilIdle()
        assertEquals("小鸡毛", vm.uiState.value.username)
        assertEquals(listOf("xiaojimao"), vm.uiState.value.posts.map { it.id })
    }

    @Test fun refresh_failure_preservesLoadedDataAndOffersRetry() = runTest(dispatcher) {
        storage.savePost(MomentPost(id = "legacy"))
        repository.posts = listOf(MomentPost(id = "remote", authorId = "xiaobai"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        repository.load = { _, _, _, _ -> throw IOException("离线") }
        vm.refreshPosts()
        advanceUntilIdle()
        assertEquals("离线", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
        assertEquals("remote", vm.uiState.value.posts.single().id)
        assertEquals("legacy", storage.getPosts().single().id)
    }

    @Test fun paging_preservesAuthorAndSearchScope_deduplicatesPosts() = runTest(dispatcher) {
        val requests = mutableListOf<List<String?>>()
        repository.load = { actor, author, query, cursor ->
            requests += listOf(actor, author, query, cursor)
            MomentsPage(if (cursor == null) listOf(MomentPost(id = "1")) else listOf(MomentPost(id = "1"), MomentPost(id = "2")), if (cursor == null) "next" else null)
        }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts(authorId = "xiaojimao", keyword = "照片")
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("1", "2"), vm.uiState.value.posts.map { it.id })
        assertEquals(listOf("xiaobai", "xiaojimao", "照片", "next"), requests.last())
        assertEquals("小鸡毛", vm.uiState.value.username)
    }

    @Test fun notifications_refreshAndClear_areAccountScoped() = runTest(dispatcher) {
        repository.notes["xiaobai"] = listOf(MomentNotification(id = "a"))
        repository.notes["xiaojimao"] = listOf(MomentNotification(id = "b"))
        val vm = MomentsNotificationsViewModel(storage, repository)
        vm.refresh()
        advanceUntilIdle()
        assertEquals("a", vm.uiState.value.notifications.single().id)
        assertEquals("xiaobai" to listOf("a"), repository.read.single())
        vm.clearAll()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.notifications.isEmpty())
        assertEquals("b", repository.notes["xiaojimao"]!!.single().id)
    }
}

private class MomentsVmTestInMemoryPrefs : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data.toMap()

    override fun getString(
        key: String,
        defValue: String?,
    ): String? = data[key] as? String ?: defValue

    override fun getStringSet(
        key: String,
        defValues: Set<String>?,
    ): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return data[key] as? Set<String> ?: defValues
    }

    override fun getInt(
        key: String,
        defValue: Int,
    ): Int = data[key] as? Int ?: defValue

    override fun getLong(
        key: String,
        defValue: Long,
    ): Long = data[key] as? Long ?: defValue

    override fun getFloat(
        key: String,
        defValue: Float,
    ): Float = data[key] as? Float ?: defValue

    override fun getBoolean(
        key: String,
        defValue: Boolean,
    ): Boolean = data[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private var clear = false

        override fun putString(
            key: String,
            value: String?,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putStringSet(
            key: String,
            values: Set<String>?,
        ): SharedPreferences.Editor {
            temp[key] = values
            return this
        }

        override fun putInt(
            key: String,
            value: Int,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putLong(
            key: String,
            value: Long,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putFloat(
            key: String,
            value: Float,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putBoolean(
            key: String,
            value: Boolean,
        ): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            temp[key] = this
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for ((k, v) in temp) {
                if (v === this) data.remove(k) else data[k] = v
            }
            temp.clear()
            clear = false
        }
    }
}
