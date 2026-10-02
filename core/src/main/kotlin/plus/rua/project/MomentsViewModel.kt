package plus.rua.project

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** 网络朋友圈状态；加载/失败与空列表分别表示，账号切换时清空上一账号内容。 */
data class MomentsUiState(
    val currentAccountId: String? = null,
    val username: String = "小白",
    val avatarPath: String? = null,
    val coverPath: String? = null,
    val posts: List<MomentPost> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasLoadedPosts: Boolean = false,
    val error: String? = null,
    val nextCursor: String? = null,
    val commentsCursor: String? = null,
    val unreadCount: Int = 0,
    val unavailable: Boolean = false,
    val searchQuery: String = "",
    val accounts: List<MomentPerson> = emptyList(),
) {
    val isRefreshing: Boolean get() = isLoading && !isLoadingMore
}

/** 朋友圈网络状态。请求捕获账号和加载版本，迟到结果不能覆盖切号后的页面。 */
class MomentsViewModel(
    private val storage: MomentsStorage,
    private var repository: MomentsRepository,
    private var timelineCache: MomentsTimelineCache = MomentsTimelineCache(),
    initialAuthorId: String? = null,
    private val restoreTimeline: Boolean = true,
) : ViewModel() {
    val accountId: String get() = MomentAccount.findById(storage.getCurrentAccountId()).id
    private val _uiState = MutableStateFlow(restoreState(accountId, initialAuthorId, null))
    val uiState = _uiState.asStateFlow()
    private var generation = 0
    private var accountGeneration = 0
    private var refreshJob: Job? = null
    private var author: String? = initialAuthorId
    private var query: String? = null
    private var detailId: String? = null
    private var commentSignature: List<String?>? = null
    private var commentRequestId = ""
    private var commentMediaId: String? = null
    private val liking = mutableSetOf<String>()

    private fun restoreState(actor: String, author: String?, keyword: String?): MomentsUiState = if (restoreTimeline) {
        timelineCache.restore(actor, author, keyword)
    } else {
        MomentsUiState(currentAccountId = actor, username = MomentAccount.findById(author ?: actor).name, isLoading = true)
    }

    fun reconnect(context: Context) {
        accountGeneration++
        repository = MomentsConnection.repository(context.applicationContext)
        timelineCache = MomentsTimelineCache.forConnection(MomentsConnection.url(context))
        timelineCache.clear()
        _uiState.value = restoreState(accountId, author, query)
        refreshPosts()
    }

    fun switchAccount(account: MomentAccount, avatarPath: String? = null) {
        accountGeneration++
        generation++
        refreshJob?.cancel()
        storage.saveCurrentAccountId(account.id)
        val accounts = _uiState.value.accounts
        val profile = accounts.firstOrNull { it.id == account.id }
        val cached = restoreState(account.id, null, null)
        _uiState.value = cached.copy(
            avatarPath = cached.avatarPath ?: profile?.avatarPath ?: avatarPath,
            coverPath = cached.coverPath ?: profile?.coverPath,
            accounts = cached.accounts.ifEmpty { accounts },
        )
        author = null
        query = null
        detailId = null
        commentSignature = null
        refreshPosts()
    }

    fun switchAccount(context: Context, account: MomentAccount) = switchAccount(account, "android.resource://${context.packageName}/${account.avatarResId}")

    fun refreshPosts(authorId: String? = author, keyword: String? = query) {
        if (author != authorId || query != keyword) _uiState.value = restoreState(accountId, authorId, keyword)
        author = authorId
        query = keyword
        load(false)
    }

    fun refreshPost(id: String) {
        detailId = id
        load(false)
    }

    fun loadMore() {
        if (!_uiState.value.isLoading && _uiState.value.nextCursor != null) load(true)
    }

    private fun load(more: Boolean) {
        refreshJob?.cancel()
        val version = ++generation
        val actor = accountId
        if (_uiState.value.currentAccountId != actor) {
            accountGeneration++
            _uiState.value = restoreState(actor, author, query)
        }
        val api = repository
        val id = detailId
        val selectedAuthor = author
        val keyword = query
        val cursor = if (more) _uiState.value.nextCursor else null
        _uiState.update { it.copy(isLoading = true, isLoadingMore = more, error = null, unavailable = false) }
        refreshJob = viewModelScope.launch {
            try {
                val accounts = api.accounts(actor)
                val profile = accounts.first { it.id == (selectedAuthor ?: actor) }
                // 账号资料不依赖动态列表成功，断网重试时选择页仍可使用已获取的头像。
                if (version == generation && actor == accountId) {
                    timelineCache.saveProfiles(actor, accounts)
                    _uiState.update { it.copy(accounts = accounts, username = profile.name, avatarPath = profile.avatarPath, coverPath = profile.coverPath) }
                }
                var commentsCursor: String? = null
                val page = if (id == null) {
                    api.posts(actor, selectedAuthor, keyword, cursor)
                } else {
                    val post = api.post(actor, id)
                    val comments = api.comments(actor, id)
                    commentsCursor = comments.nextCursor
                    MomentsPage(listOf(post.copy(comments = comments.items)))
                }
                val unread = api.notifications(actor).unreadCount
                if (version == generation && actor == accountId) {
                    _uiState.update {
                        it.copy(
                            currentAccountId = actor, username = profile.name, avatarPath = profile.avatarPath, coverPath = profile.coverPath,
                            posts = if (more) (it.posts + page.items).distinctBy { post -> post.id } else page.items,
                            nextCursor = page.nextCursor, commentsCursor = commentsCursor, unreadCount = unread, isLoading = false, isLoadingMore = false, hasLoadedPosts = true, searchQuery = keyword.orEmpty(),
                        )
                    }
                    if (id == null) timelineCache.save(actor, selectedAuthor, keyword, _uiState.value)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation && actor == accountId) {
                    _uiState.update {
                        val missing = id != null && e is MomentsApiException && e.status == 404
                        it.copy(isLoading = false, isLoadingMore = false, error = e.message ?: "加载失败，请重试", unavailable = missing, posts = if (missing) emptyList() else it.posts)
                    }
                }
            }
        }
    }

    fun loadMoreComments() {
        val id = detailId ?: return
        val cursor = _uiState.value.commentsCursor ?: return
        if (_uiState.value.isLoading) return
        val actor = accountId
        val version = generation
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                val page = repository.comments(actor, id, cursor)
                if (version == generation && actor == accountId) {
                    _uiState.update {
                        it.copy(isLoading = false, commentsCursor = page.nextCursor, posts = it.posts.map { p -> p.copy(comments = (p.comments + page.items).distinctBy { c -> c.id }) })
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation && actor == accountId) _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun mutate(action: suspend (MomentsRepository, String) -> Unit) {
        val actor = accountId
        val api = repository
        val cache = timelineCache
        val version = generation
        viewModelScope.launch {
            try {
                action(api, actor)
                cache.invalidatePosts(actor)
                if (actor == accountId && version == generation) {
                    load(false)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (actor == accountId && version == generation) _uiState.update { it.copy(error = e.message ?: "操作失败，请重试") }
            }
        }
    }

    fun toggleLike(postId: String) {
        val post = _uiState.value.posts.find { it.id == postId } ?: return
        val key = "$accountId:$postId"
        if (!liking.add(key)) return
        mutate { api, actor ->
            try {
                api.like(actor, postId, !post.isLikedByMe)
            } finally {
                liking.remove(key)
            }
        }
    }
    fun deletePost(id: String) = mutate { api, actor -> api.deletePost(actor, id) }
    fun deleteComment(id: String) = mutate { api, actor -> api.deleteComment(actor, id) }
    fun setVisibility(id: String, visibility: String) = mutate { api, actor -> api.visibility(actor, id, visibility) }

    /** 失败时保留同一请求 ID 和已上传图片，网络超时后的重试不会产生第二条评论。 */
    suspend fun sendComment(context: Context, id: String, text: String, replyTo: String?, photo: Uri?): Boolean {
        val actor = accountId
        val api = repository
        val cache = timelineCache
        val signature = listOf(actor, id, text.trim(), replyTo, photo?.toString())
        if (signature != commentSignature) {
            commentSignature = signature
            commentRequestId = UUID.randomUUID().toString()
            commentMediaId = null
        }
        if (photo != null && commentMediaId == null) {
            val file = copyMomentPhoto(context, photo)
            try {
                commentMediaId = api.upload(actor, file)
            } finally {
                file.delete()
            }
        }
        try {
            api.comment(actor, id, commentRequestId, text.trim(), commentMediaId, replyTo)
        } catch (e: MomentsApiException) {
            if (e.status == 400 && e.message == "图片不存在或不属于当前账号") commentMediaId = null
            throw e
        }
        commentSignature = null
        cache.invalidatePosts(actor)
        if (actor == accountId) refreshPost(id)
        return true
    }

    fun setAvatarFromUri(context: Context, uri: Uri) = setProfile(context, uri, false)
    fun setCoverFromUri(context: Context, uri: Uri) = setProfile(context, uri, true)

    /** 恢复请求始终使用开始时的账号；切号或换服务后的迟到结果不能覆盖新页面。失败抛给菜单供重试。 */
    suspend fun resetAvatar(): Boolean {
        val actor = accountId
        val api = repository
        val version = accountGeneration
        val profile = api.resetAvatar(actor)
        if (actor != accountId || version != accountGeneration) return false
        _uiState.update {
            val accounts = if (it.accounts.any { account -> account.id == actor }) {
                it.accounts.map { account -> if (account.id == actor) profile else account }
            } else {
                it.accounts + profile
            }
            it.copy(avatarPath = profile.avatarPath, coverPath = profile.coverPath, accounts = accounts, error = null)
        }
        timelineCache.saveProfiles(actor, _uiState.value.accounts)
        refreshPosts()
        return true
    }

    private fun setProfile(context: Context, uri: Uri, cover: Boolean) = mutate { api, actor ->
        val file = copyMomentPhoto(context, uri)
        try {
            api.profile(actor, api.upload(actor, file), cover)
        } finally {
            file.delete()
        }
    }

    companion object {
        const val MOMENTS_DIR_NAME = "moments"
        fun fromContext(context: Context, cacheTimeline: Boolean = false, authorId: String? = null): MomentsViewModel = MomentsViewModel(
            MomentsStorage.fromContext(context),
            MomentsConnection.repository(context.applicationContext),
            MomentsTimelineCache.forConnection(MomentsConnection.url(context)),
            initialAuthorId = authorId,
            restoreTimeline = cacheTimeline,
        )
    }
}

/** 将系统图片流复制到临时文件；文件数量由调用方的成功/失败清理负责。 */
internal suspend fun copyMomentPhoto(context: Context, uri: Uri): File = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val file = File.createTempFile("moment-upload-", ".image", context.cacheDir)
    try {
        val input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
        input.use { source ->
            file.outputStream().use { target ->
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= 10 * 1024 * 1024) { "图片不能超过 10 MiB" }
                    target.write(buffer, 0, read)
                }
            }
        }
        file
    } catch (e: Exception) {
        file.delete()
        throw e
    }
}
