package plus.rua.project

import android.content.SharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
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

    @Test fun initialState_beforeFirstRequest_isLoadingWithoutEmptyResult() {
        val state = MomentsViewModel(storage, repository).uiState.value
        assertTrue(state.isLoading)
        assertTrue(state.isRefreshing)
        assertFalse(state.hasLoadedPosts)
    }

    @Test fun firstLoad_emptyResponse_reportsEmptyOnlyAfterSuccess() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentsPage<MomentPost>>()
        repository.load = { _, _, _, _ -> response.await() }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)
        assertFalse(vm.uiState.value.hasLoadedPosts)

        response.complete(MomentsPage(emptyList()))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hasLoadedPosts)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.posts.isEmpty())
    }

    @Test fun refresh_inFlight_keepsPostsAndProfileVisible() = runTest(dispatcher) {
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "avatar", "cover"))
        repository.posts = listOf(MomentPost(id = "visible", authorId = "xiaobai"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        val response = CompletableDeferred<MomentsPage<MomentPost>>()
        repository.load = { _, _, _, _ -> response.await() }

        vm.refreshPosts()
        runCurrent()

        assertTrue(vm.uiState.value.isRefreshing)
        assertEquals("visible", vm.uiState.value.posts.single().id)
        assertEquals("avatar", vm.uiState.value.avatarPath)
        assertEquals("cover", vm.uiState.value.coverPath)
        response.complete(MomentsPage(repository.posts))
        advanceUntilIdle()
    }

    @Test fun cachedFeed_openPersonalAlbum_previewsOnlyThatAuthorsPostsAndProfile() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "avatar", "cover"), MomentPerson("xiaojimao", "小鸡毛"))
        repository.posts = listOf(MomentPost(id = "own", authorId = "xiaobai"), MomentPost(id = "other", authorId = "xiaojimao"))
        val feed = MomentsViewModel(storage, repository, cache)
        feed.refreshPosts()
        advanceUntilIdle()

        val album = MomentsViewModel(storage, repository, cache, initialAuthorId = "xiaobai")

        assertEquals("小白", album.uiState.value.username)
        assertEquals("avatar", album.uiState.value.avatarPath)
        assertEquals("cover", album.uiState.value.coverPath)
        assertEquals(listOf("own"), album.uiState.value.posts.map { it.id })
        assertTrue(album.uiState.value.isLoading)
        // 首页的一页动态是预览，不表示作者相册已经请求成功。
        assertFalse(album.uiState.value.hasLoadedPosts)
    }

    @Test fun cachedFeed_authorNotInFirstPage_doesNotClaimEmptyAlbum() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.posts = listOf(MomentPost(id = "other", authorId = "xiaojimao"))
        val feed = MomentsViewModel(storage, repository, cache)
        feed.refreshPosts()
        advanceUntilIdle()

        val album = MomentsViewModel(storage, repository, cache, initialAuthorId = "xiaobai")

        assertTrue(album.uiState.value.posts.isEmpty())
        assertTrue(album.uiState.value.isLoading)
        assertFalse(album.uiState.value.hasLoadedPosts)
    }

    @Test fun cachedTimeline_reenterThenFail_keepsLastSuccessfulResult() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.posts = listOf(MomentPost(id = "cached", authorId = "xiaobai"))
        val first = MomentsViewModel(storage, repository, cache)
        first.refreshPosts()
        advanceUntilIdle()

        val reentered = MomentsViewModel(storage, repository, cache)
        assertEquals("cached", reentered.uiState.value.posts.single().id)
        assertTrue(reentered.uiState.value.hasLoadedPosts)
        repository.load = { _, _, _, _ -> throw IOException("离线") }
        reentered.refreshPosts()
        advanceUntilIdle()

        assertEquals("cached", reentered.uiState.value.posts.single().id)
        assertEquals("离线", reentered.uiState.value.error)
        assertFalse(reentered.uiState.value.isLoading)
    }

    @Test fun cachedTimeline_emptyAlbum_doesNotReuseOutdatedFeedPreview() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.posts = listOf(MomentPost(id = "old", authorId = "xiaobai"))
        val feed = MomentsViewModel(storage, repository, cache)
        feed.refreshPosts()
        advanceUntilIdle()
        repository.posts = emptyList()
        val album = MomentsViewModel(storage, repository, cache, initialAuthorId = "xiaobai")
        album.refreshPosts(authorId = "xiaobai")
        advanceUntilIdle()

        val reentered = MomentsViewModel(storage, repository, cache, initialAuthorId = "xiaobai")

        assertTrue(reentered.uiState.value.posts.isEmpty())
        assertTrue(reentered.uiState.value.hasLoadedPosts)
    }

    @Test fun cachedTimeline_differentAccountAndConnection_neverReusesPrivatePosts() = runTest(dispatcher) {
        val cache = MomentsTimelineCache.forConnection("test://private-source")
        cache.clear()
        repository.posts = listOf(MomentPost(id = "private", authorId = "xiaobai", visibility = "私密"))
        val vm = MomentsViewModel(storage, repository, cache)
        vm.refreshPosts()
        advanceUntilIdle()

        val otherConnection = MomentsViewModel(storage, repository, MomentsTimelineCache.forConnection("test://other-source"))
        assertTrue(otherConnection.uiState.value.posts.isEmpty())
        storage.saveCurrentAccountId("xiaojimao")
        val otherAccount = MomentsViewModel(storage, repository, cache)
        assertEquals("xiaojimao", otherAccount.uiState.value.currentAccountId)
        assertTrue(otherAccount.uiState.value.posts.isEmpty())
        assertFalse(otherAccount.uiState.value.hasLoadedPosts)
    }

    @Test fun paging_inFlight_usesFooterLoadingAndKeepsExistingPosts() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentsPage<MomentPost>>()
        repository.load = { _, _, _, cursor ->
            if (cursor == null) MomentsPage(listOf(MomentPost(id = "first")), "next") else response.await()
        }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()

        vm.loadMore()
        runCurrent()

        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.isLoadingMore)
        assertFalse(vm.uiState.value.isRefreshing)
        assertEquals("first", vm.uiState.value.posts.single().id)
        response.complete(MomentsPage(listOf(MomentPost(id = "second"))))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoadingMore)
        assertEquals(listOf("first", "second"), vm.uiState.value.posts.map { it.id })
    }

    @Test fun cachedTimeline_detailScreen_doesNotShowUnrelatedFeedPost() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.posts = listOf(MomentPost(id = "unrelated", authorId = "xiaobai"))
        val vm = MomentsViewModel(storage, repository, cache)
        vm.refreshPosts()
        advanceUntilIdle()

        val detail = MomentsViewModel(storage, repository, cache, restoreTimeline = false)

        assertTrue(detail.uiState.value.posts.isEmpty())
        assertFalse(detail.uiState.value.hasLoadedPosts)
    }

    @Test fun cachedTimeline_newSearchQuery_doesNotReuseFeedOrOtherQuery() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.load = { _, _, query, _ -> MomentsPage(listOf(MomentPost(id = query ?: "feed"))) }
        val vm = MomentsViewModel(storage, repository, cache)
        vm.refreshPosts()
        advanceUntilIdle()
        vm.refreshPosts(keyword = "照片")
        advanceUntilIdle()
        assertEquals("照片", vm.uiState.value.posts.single().id)

        vm.refreshPosts(keyword = "旅行")

        assertTrue(vm.uiState.value.posts.isEmpty())
        assertFalse(vm.uiState.value.hasLoadedPosts)
        advanceUntilIdle()
        assertEquals("旅行", vm.uiState.value.posts.single().id)
    }

    @Test fun cachedTimeline_deleteFromDetail_invalidatesFeedSnapshot() = runTest(dispatcher) {
        val cache = MomentsTimelineCache()
        repository.posts = listOf(MomentPost(id = "deleted", authorId = "xiaobai"))
        val feed = MomentsViewModel(storage, repository, cache)
        feed.refreshPosts()
        advanceUntilIdle()
        val detail = MomentsViewModel(storage, repository, cache, restoreTimeline = false)
        detail.refreshPost("deleted")
        advanceUntilIdle()

        detail.deletePost("deleted")
        advanceUntilIdle()
        val reentered = MomentsViewModel(storage, repository, cache)

        assertTrue(reentered.uiState.value.posts.isEmpty())
        assertFalse(reentered.uiState.value.hasLoadedPosts)
        assertEquals(repository.profiles, reentered.uiState.value.accounts)
    }

    @Test fun resetAvatar_updatesCurrentProfile_andKeepsCoverAndOtherAccount() = runTest(dispatcher) {
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "white-avatar", "white-cover"), MomentPerson("xiaojimao", "小鸡毛", "chicken-avatar"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()

        assertTrue(vm.resetAvatar())

        assertEquals(null, vm.uiState.value.avatarPath)
        assertEquals("white-cover", vm.uiState.value.coverPath)
        assertEquals("chicken-avatar", vm.uiState.value.accounts.first { it.id == "xiaojimao" }.avatarPath)
        assertEquals(listOf("xiaobai"), repository.avatarResets)
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.accounts.first { it.id == "xiaobai" }.avatarPath)
    }

    @Test fun resetAvatar_switchAwayAndBack_rejectsLateResult() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentPerson>()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "white-avatar"), MomentPerson("xiaojimao", "小鸡毛", "chicken-avatar"))
        repository.resetAvatarRequest = { response.await() }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        val result = async { vm.resetAvatar() }
        runCurrent()

        vm.switchAccount(MomentAccount.ACCOUNT_XIAOJIMAO)
        advanceUntilIdle()
        assertEquals("chicken-avatar", vm.uiState.value.avatarPath)
        repository.profiles = repository.profiles.map { if (it.id == "xiaobai") it.copy(avatarPath = "new-white-avatar") else it }
        vm.switchAccount(MomentAccount.ACCOUNT_XIAOBAI)
        advanceUntilIdle()
        response.complete(MomentPerson("xiaobai", "小白"))
        assertFalse(result.await())
        assertEquals("new-white-avatar", vm.uiState.value.avatarPath)
        assertEquals(listOf("xiaobai"), repository.avatarResets)
    }

    @Test fun resetAvatar_failure_preservesProfile_andCanRetry() = runTest(dispatcher) {
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "white-avatar", "white-cover"), MomentPerson("xiaojimao", "小鸡毛"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        repository.resetAvatarRequest = { throw IOException("网络超时") }

        val error = runCatching { vm.resetAvatar() }.exceptionOrNull()

        assertEquals("网络超时", error?.message)
        assertEquals("white-avatar", vm.uiState.value.avatarPath)
        assertEquals("white-cover", vm.uiState.value.coverPath)
        repository.resetAvatarRequest = null
        assertTrue(vm.resetAvatar())
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.avatarPath)
    }

    @Test fun switchAccount_usesFetchedAvatarBeforeReload_andRetainsAccountProfiles() = runTest(dispatcher) {
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "white-avatar"), MomentPerson("xiaojimao", "小鸡毛", "chicken-avatar"))
        repository.posts = listOf(MomentPost(id = "private-a", authorId = "xiaobai", visibility = "私密"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()

        vm.switchAccount(MomentAccount.ACCOUNT_XIAOJIMAO, "built-in-avatar")

        assertEquals("chicken-avatar", vm.uiState.value.avatarPath)
        assertEquals(repository.profiles, vm.uiState.value.accounts)
        assertTrue(vm.uiState.value.posts.isEmpty())
        advanceUntilIdle()
        assertEquals("chicken-avatar", vm.uiState.value.avatarPath)
    }

    @Test fun refresh_timelineFails_keepsFetchedAccountAvatars() = runTest(dispatcher) {
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "white-avatar"), MomentPerson("xiaojimao", "小鸡毛", "chicken-avatar"))
        repository.load = { _, _, _, _ -> throw IOException("动态加载失败") }
        val vm = MomentsViewModel(storage, repository)

        vm.refreshPosts()
        advanceUntilIdle()

        assertEquals(repository.profiles, vm.uiState.value.accounts)
        assertEquals("white-avatar", vm.uiState.value.avatarPath)
        assertEquals("动态加载失败", vm.uiState.value.error)
    }

    @Test fun externalAccountChange_refreshImmediatelyDropsPreviousPrivateData() = runTest(dispatcher) {
        repository.posts = listOf(MomentPost(id = "private-a", authorId = "xiaobai", visibility = "私密"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        storage.saveCurrentAccountId("xiaojimao")
        repository.posts = emptyList()
        vm.refreshPosts()
        assertTrue(vm.uiState.value.posts.isEmpty())
        assertEquals("xiaojimao", vm.uiState.value.currentAccountId)
        advanceUntilIdle()
        assertEquals("小鸡毛", vm.uiState.value.username)
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
