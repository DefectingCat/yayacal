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
import java.io.File
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

    @Test fun uncachedSearch_failureKeepsQueryAndCanRetrySameKeyword() = runTest(dispatcher) {
        repository.load = { _, _, _, _ -> throw IOException("网络不可用") }
        val vm = MomentsViewModel(storage, repository, restoreTimeline = false)
        vm.refreshPosts(authorId = "xiaobai", keyword = "散步")
        advanceUntilIdle()
        assertEquals("散步", vm.uiState.value.searchQuery)
        assertEquals("网络不可用", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.hasLoadedPosts)

        repository.load = { _, _, _, _ -> MomentsPage(emptyList()) }
        vm.refreshPosts(authorId = "xiaobai", keyword = "散步")
        advanceUntilIdle()
        assertEquals("散步", vm.uiState.value.searchQuery)
        assertEquals(null, vm.uiState.value.error)
        assertTrue(vm.uiState.value.hasLoadedPosts)
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

    @Test fun setCover_resumeRefreshDuringSave_updatesProfileAndCacheWithoutReentry() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentPerson>()
        val cache = MomentsTimelineCache()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "white-avatar", "old-cover"), MomentPerson("xiaojimao", "小鸡毛", "chicken-avatar"))
        repository.profileRequest = { _, _, _ -> response.await() }
        val vm = MomentsViewModel(storage, repository, cache, initialAuthorId = "xiaobai")
        vm.refreshPosts()
        advanceUntilIdle()
        val photo = profilePhoto()

        vm.setProfilePhoto(cover = true) { photo }
        runCurrent()
        vm.refreshPosts(authorId = "xiaobai")
        advanceUntilIdle()
        assertEquals("old-cover", vm.uiState.value.coverPath)
        // 保存成功后即使动态列表不可用，封面也必须立即更新并供其他页面复用。
        repository.load = { _, _, _, _ -> throw IOException("列表不可用") }
        response.complete(MomentPerson("xiaobai", "小白", "white-avatar", "new-cover"))
        advanceUntilIdle()

        assertEquals("new-cover", vm.uiState.value.coverPath)
        assertEquals("white-avatar", vm.uiState.value.avatarPath)
        assertEquals("new-cover", vm.uiState.value.accounts.first { it.id == "xiaobai" }.coverPath)
        assertEquals("chicken-avatar", vm.uiState.value.accounts.first { it.id == "xiaojimao" }.avatarPath)
        assertEquals("列表不可用", vm.uiState.value.error)
        assertEquals("new-cover", MomentsViewModel(storage, repository, cache).uiState.value.coverPath)
        assertFalse(photo.exists())
    }

    @Test fun setAvatar_resumeRefreshDuringSave_updatesAvatarAndKeepsCover() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentPerson>()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", "old-avatar", "white-cover"))
        repository.profileRequest = { _, _, _ -> response.await() }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        val photo = profilePhoto()

        vm.setProfilePhoto(cover = false) { photo }
        runCurrent()
        vm.refreshPosts()
        advanceUntilIdle()
        response.complete(MomentPerson("xiaobai", "小白", "new-avatar", "white-cover"))
        advanceUntilIdle()

        assertEquals("new-avatar", vm.uiState.value.avatarPath)
        assertEquals("white-cover", vm.uiState.value.coverPath)
        assertFalse(repository.profileUpdates.single().third)
        assertFalse(photo.exists())
    }

    @Test fun setCover_latePreSaveRefresh_doesNotRestoreOldCover() = runTest(dispatcher) {
        val saved = CompletableDeferred<MomentPerson>()
        val oldRefresh = CompletableDeferred<List<MomentPerson>>()
        val cache = MomentsTimelineCache()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", coverPath = "old-cover"))
        repository.profileRequest = { _, _, _ -> saved.await() }
        val vm = MomentsViewModel(storage, repository, cache)
        vm.refreshPosts()
        advanceUntilIdle()
        val oldProfiles = repository.profiles
        var reads = 0
        repository.accountsRequest = {
            if (reads++ == 0) withContext(NonCancellable) { oldRefresh.await() } else repository.profiles
        }
        val photo = profilePhoto()

        vm.setProfilePhoto(cover = true) { photo }
        runCurrent()
        vm.refreshPosts()
        runCurrent()
        saved.complete(MomentPerson("xiaobai", "小白", coverPath = "new-cover"))
        runCurrent()
        assertEquals("new-cover", vm.uiState.value.coverPath)
        oldRefresh.complete(oldProfiles)
        advanceUntilIdle()

        assertEquals("new-cover", vm.uiState.value.coverPath)
        assertEquals("new-cover", MomentsViewModel(storage, repository, cache).uiState.value.coverPath)
        assertFalse(photo.exists())
    }

    @Test fun setCover_switchAwayAndBack_rejectsLateResultAndKeepsCapturedActor() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentPerson>()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", coverPath = "white-cover"), MomentPerson("xiaojimao", "小鸡毛", coverPath = "chicken-cover"))
        repository.profileRequest = { _, _, _ -> response.await() }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        val photo = profilePhoto()

        vm.setProfilePhoto(cover = true) { photo }
        runCurrent()
        vm.switchAccount(MomentAccount.ACCOUNT_XIAOJIMAO)
        advanceUntilIdle()
        vm.switchAccount(MomentAccount.ACCOUNT_XIAOBAI)
        advanceUntilIdle()
        response.complete(MomentPerson("xiaobai", "小白", coverPath = "late-cover"))
        advanceUntilIdle()

        assertEquals("white-cover", vm.uiState.value.coverPath)
        assertEquals(listOf("xiaobai"), repository.uploads)
        assertEquals("xiaobai", repository.profileUpdates.single().first)
        assertFalse(photo.exists())
    }

    @Test fun setCover_authorChangesDuringSave_preservesViewedProfile() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentPerson>()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", coverPath = "white-cover"), MomentPerson("xiaojimao", "小鸡毛", coverPath = "chicken-cover"))
        repository.profileRequest = { _, _, _ -> response.await() }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        val photo = profilePhoto()

        vm.setProfilePhoto(cover = true) { photo }
        runCurrent()
        vm.refreshPosts(authorId = "xiaojimao")
        advanceUntilIdle()
        response.complete(MomentPerson("xiaobai", "小白", coverPath = "new-white-cover"))
        advanceUntilIdle()

        assertEquals("小鸡毛", vm.uiState.value.username)
        assertEquals("chicken-cover", vm.uiState.value.coverPath)
        assertEquals("new-white-cover", vm.uiState.value.accounts.first { it.id == "xiaobai" }.coverPath)
        assertFalse(photo.exists())
    }

    @Test fun setCover_failureAfterResume_keepsCoverAndShowsErrorAndCanRetry() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentPerson>()
        repository.profiles = listOf(MomentPerson("xiaobai", "小白", coverPath = "old-cover"))
        repository.profileRequest = { _, _, _ -> response.await() }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        val photo = profilePhoto()

        vm.setProfilePhoto(cover = true) { photo }
        runCurrent()
        vm.refreshPosts()
        advanceUntilIdle()
        response.completeExceptionally(IOException("保存超时"))
        advanceUntilIdle()

        assertEquals("old-cover", vm.uiState.value.coverPath)
        assertEquals("保存超时", vm.uiState.value.operationError)
        assertFalse(photo.exists())
        repository.profileRequest = null
        val retry = profilePhoto()
        vm.setProfilePhoto(cover = true) { retry }
        advanceUntilIdle()
        assertEquals(repository.profileUpdates.last().second, vm.uiState.value.coverPath)
        assertEquals(null, vm.uiState.value.operationError)
        assertFalse(retry.exists())
    }

    private fun profilePhoto(): File = File.createTempFile("moments-profile-test-", ".image").apply {
        writeText("photo")
        deleteOnExit()
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

    @Test fun paging_failure_keepsCursorAndOnlySetsFooterError() = runTest(dispatcher) {
        var fail = true
        repository.load = { _, _, _, cursor ->
            if (cursor != null && fail) throw IOException("离线")
            MomentsPage(listOf(MomentPost(id = if (cursor == null) "first" else "second")), if (cursor == null) "next" else null)
        }
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.error)
        assertEquals("离线", vm.uiState.value.loadMoreError)
        assertEquals("next", vm.uiState.value.nextCursor)
        assertEquals(listOf("first"), vm.uiState.value.posts.map { it.id })
        fail = false
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.loadMoreError)
        assertEquals(listOf("first", "second"), vm.uiState.value.posts.map { it.id })
    }

    @Test fun comments_pagingFailure_doesNotBecomePageRefreshFailure() = runTest(dispatcher) {
        repository.posts = listOf(MomentPost(id = "post", authorId = "xiaobai"))
        val response = CompletableDeferred<MomentsPage<MomentComment>>()
        repository.commentsRequest = { _, _, cursor -> if (cursor == null) MomentsPage(emptyList(), "next") else response.await() }
        val vm = MomentsViewModel(storage, repository, restoreTimeline = false)
        vm.refreshPost("post")
        advanceUntilIdle()
        vm.loadMoreComments()
        runCurrent()
        assertTrue(vm.uiState.value.isLoadingMore)
        assertFalse(vm.uiState.value.isRefreshing)
        response.completeExceptionally(IOException("网络超时"))
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.error)
        assertEquals("网络超时", vm.uiState.value.loadMoreError)
        assertEquals("post", vm.uiState.value.posts.single().id)
        assertFalse(vm.uiState.value.isLoadingMore)
    }

    @Test fun like_failure_isAnOperationErrorAndKeepsPageUsable() = runTest(dispatcher) {
        repository.posts = listOf(MomentPost(id = "post", authorId = "xiaobai"))
        val vm = MomentsViewModel(storage, repository)
        vm.refreshPosts()
        advanceUntilIdle()
        repository.mutationFailure = IOException("离线")
        vm.toggleLike("post")
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.error)
        assertEquals("离线", vm.uiState.value.operationError)
        assertEquals("post", vm.uiState.value.posts.single().id)
        vm.dismissOperationError()
        assertEquals(null, vm.uiState.value.operationError)
    }

    @Test fun notifications_initialAndEmpty_areDistinctStates() = runTest(dispatcher) {
        val vm = MomentsNotificationsViewModel(storage, repository)
        assertTrue(vm.uiState.value.isRefreshing)
        assertFalse(vm.uiState.value.hasLoaded)
        vm.refresh()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isRefreshing)
        assertTrue(vm.uiState.value.hasLoaded)
        assertTrue(vm.uiState.value.notifications.isEmpty())
    }

    @Test fun notifications_pagingFailure_preservesRowsAndRetryCursor() = runTest(dispatcher) {
        val response = CompletableDeferred<MomentsPage<MomentNotification>>()
        repository.notificationsRequest = { _, cursor ->
            if (cursor == null) MomentsPage(listOf(MomentNotification(id = "first")), "next") else response.await()
        }
        val vm = MomentsNotificationsViewModel(storage, repository)
        vm.refresh()
        advanceUntilIdle()
        vm.refresh(more = true)
        runCurrent()
        assertTrue(vm.uiState.value.isLoadingMore)
        assertFalse(vm.uiState.value.isRefreshing)
        response.completeExceptionally(IOException("离线"))
        advanceUntilIdle()
        assertEquals(null, vm.uiState.value.error)
        assertEquals("离线", vm.uiState.value.loadMoreError)
        assertEquals("first", vm.uiState.value.notifications.single().id)
        assertEquals("next", vm.uiState.value.nextCursor)
    }

    @Test fun notifications_readOrDeleteFailure_doesNotReplaceLoadedList() = runTest(dispatcher) {
        repository.notes["xiaobai"] = listOf(MomentNotification(id = "first"))
        repository.notificationsReadFailure = IOException("离线")
        val vm = MomentsNotificationsViewModel(storage, repository)
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hasLoaded)
        assertEquals(null, vm.uiState.value.error)
        assertTrue(vm.uiState.value.operationError!!.contains("标记为已读"))
        repository.notificationsDeleteFailure = IOException("网络超时")
        vm.clearAll()
        advanceUntilIdle()
        assertEquals("网络超时", vm.uiState.value.operationError)
        assertEquals("first", vm.uiState.value.notifications.single().id)
        assertEquals(null, vm.uiState.value.error)
    }

    @Test fun detail_deletedPost_reportsUnavailableAndClearsStaleContent() = runTest(dispatcher) {
        repository.posts = listOf(MomentPost(id = "post", authorId = "xiaobai"))
        val vm = MomentsViewModel(storage, repository, restoreTimeline = false)
        vm.refreshPost("post")
        advanceUntilIdle()
        repository.posts = emptyList()
        vm.refreshPost("post")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.unavailable)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.posts.isEmpty())
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
