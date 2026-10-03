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
    val loadMoreError: String? = null,
    val operationError: String? = null,
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
    private var pendingCommentRefresh: String? = null
    private var commentSignature: List<String?>? = null
    private var commentRequestId = ""
    private var commentMediaId: String? = null
    private val liking = mutableSetOf<String>()

    private fun restoreState(actor: String, author: String?, keyword: String?): MomentsUiState = if (restoreTimeline) {
        timelineCache.restore(actor, author, keyword)
    } else {
        MomentsUiState(currentAccountId = actor, username = MomentAccount.findById(author ?: actor).name, isLoading = true, searchQuery = keyword.orEmpty())
    }

    fun reconnect(context: Context) {
        accountGeneration++
        pendingCommentRefresh = null
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
        pendingCommentRefresh = null
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

    private fun load(more: Boolean): Job {
        refreshJob?.cancel()
        val version = ++generation
        val actor = accountId
        if (_uiState.value.currentAccountId != actor) {
            accountGeneration++
            pendingCommentRefresh = null
            _uiState.value = restoreState(actor, author, query)
        }
        val api = repository
        val id = detailId
        val loadAllComments = id != null && pendingCommentRefresh == id
        val loadedCommentCount = _uiState.value.posts.firstOrNull { it.id == id }?.comments?.size ?: 0
        val selectedAuthor = author
        val keyword = query
        val cursor = if (more) _uiState.value.nextCursor else null
        _uiState.update { it.copy(isLoading = true, isLoadingMore = more, error = if (more) it.error else null, loadMoreError = null, unavailable = false) }
        val job = viewModelScope.launch {
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
                    var comments = api.comments(actor, id)
                    val items = comments.items.toMutableList()
                    // 普通刷新保留已读到的评论范围；发送后读到尾页，才能显示服务端确认的新评论。
                    while (comments.nextCursor != null && (loadAllComments || items.size < loadedCommentCount)) {
                        comments = api.comments(actor, id, comments.nextCursor)
                        items += comments.items
                    }
                    commentsCursor = comments.nextCursor
                    MomentsPage(listOf(post.copy(comments = items.distinctBy { it.id })))
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
                    if (id != null && pendingCommentRefresh == id) pendingCommentRefresh = null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation && actor == accountId) {
                    _uiState.update {
                        val missing = id != null && e is MomentsApiException && e.status == 404
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = if (more) it.error else e.message ?: "加载失败，请重试",
                            loadMoreError = if (more) e.message ?: "加载失败，请重试" else null,
                            unavailable = missing,
                            posts = if (missing) emptyList() else it.posts,
                        )
                    }
                }
            }
        }
        refreshJob = job
        return job
    }

    fun loadMoreComments() {
        val id = detailId ?: return
        val cursor = _uiState.value.commentsCursor ?: return
        if (_uiState.value.isLoading) return
        val actor = accountId
        val version = generation
        _uiState.update { it.copy(isLoading = true, isLoadingMore = true, loadMoreError = null) }
        refreshJob = viewModelScope.launch {
            try {
                val page = repository.comments(actor, id, cursor)
                if (version == generation && actor == accountId) {
                    _uiState.update {
                        it.copy(isLoading = false, isLoadingMore = false, commentsCursor = page.nextCursor, posts = it.posts.map { p -> p.copy(comments = (p.comments + page.items).distinctBy { c -> c.id }) })
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (version == generation && actor == accountId) _uiState.update { it.copy(isLoading = false, isLoadingMore = false, loadMoreError = e.message ?: "评论加载失败") }
            }
        }
    }

    fun dismissOperationError() {
        _uiState.update { it.copy(operationError = null) }
    }

    private fun mutate(postId: String? = null, action: suspend (MomentsRepository, String) -> Unit) {
        val actor = accountId
        val api = repository
        val cache = timelineCache
        val version = generation
        _uiState.update { it.copy(operationError = null) }
        viewModelScope.launch {
            try {
                action(api, actor)
                cache.invalidatePosts(actor)
                if (actor == accountId && version == generation) {
                    if (postId == null) {
                        load(false)
                    } else {
                        val updated = api.post(actor, postId)
                        if (actor == accountId && version == generation) {
                            // 点赞只更新该条动态，保留列表分页以及详情中已加载的完整评论。
                            _uiState.update { state ->
                                state.copy(
                                    posts = state.posts.map { post ->
                                        if (post.id != postId) {
                                            post
                                        } else if (detailId == postId) {
                                            updated.copy(comments = post.comments)
                                        } else {
                                            updated
                                        }
                                    },
                                )
                            }
                            if (detailId == null) cache.save(actor, author, query, _uiState.value)
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (actor == accountId && version == generation) _uiState.update { it.copy(operationError = e.message ?: "操作失败，请重试") }
            }
        }
    }

    fun toggleLike(postId: String) {
        val post = _uiState.value.posts.find { it.id == postId } ?: return
        val key = "$accountId:$postId"
        if (!liking.add(key)) return
        mutate(postId) { api, actor ->
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

    /** 失败时保留请求 ID 与图片；服务端确认后等待评论同步，再通知界面清空草稿并滚动。 */
    suspend fun sendComment(context: Context, id: String, text: String, replyTo: String?, photo: Uri?): Boolean = sendComment(
        id,
        text,
        replyTo,
        photo?.toString(),
        photo?.let { uri -> suspend { copyMomentPhoto(context, uri) } },
    )

    /** 图片准备可注入，文字评论不依赖 Android Context；读取失败不撤销已经确认的评论写入。 */
    internal suspend fun sendComment(id: String, text: String, replyTo: String?, photoKey: String? = null, preparePhoto: (suspend () -> File)? = null): Boolean {
        val actor = accountId
        val api = repository
        val cache = timelineCache
        val version = accountGeneration
        val signature = listOf(actor, id, text.trim(), replyTo, photoKey)
        if (signature != commentSignature) {
            commentSignature = signature
            commentRequestId = UUID.randomUUID().toString()
            commentMediaId = null
        }
        if (preparePhoto != null && commentMediaId == null) {
            val file = preparePhoto()
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
        if (actor == accountId && version == accountGeneration && (detailId == null || detailId == id)) {
            pendingCommentRefresh = id
            detailId = id
            load(false).join()
        }
        return true
    }

    fun setAvatarFromUri(context: Context, uri: Uri) = setProfilePhoto(cover = false) { copyMomentPhoto(context, uri) }
    fun setCoverFromUri(context: Context, uri: Uri) = setProfilePhoto(cover = true) { copyMomentPhoto(context, uri) }

    /** 恢复请求始终使用开始时的账号；切号或换服务后的迟到结果不能覆盖新页面。失败抛给菜单供重试。 */
    suspend fun resetAvatar(): Boolean {
        val actor = accountId
        val api = repository
        val version = accountGeneration
        val profile = api.resetAvatar(actor)
        if (actor != accountId || version != accountGeneration) return false
        applyProfile(actor, profile)
        return true
    }

    private fun applyProfile(actor: String, profile: MomentPerson) {
        _uiState.update {
            val accounts = if (it.accounts.any { account -> account.id == actor }) {
                it.accounts.map { account -> if (account.id == actor) profile else account }
            } else {
                it.accounts + profile
            }
            if ((author ?: actor) == actor) {
                it.copy(username = profile.name, avatarPath = profile.avatarPath, coverPath = profile.coverPath, accounts = accounts, error = null, operationError = null)
            } else {
                it.copy(accounts = accounts, operationError = null)
            }
        }
        timelineCache.saveProfiles(actor, _uiState.value.accounts)
        // 保存后的刷新使仍在途的旧资料请求失效，避免列表加载完成后把新封面覆盖回去。
        refreshPosts()
    }

    /** 写入固定开始时的账号和连接；普通列表刷新不使保存失效，临时图片在请求结束后清理。 */
    internal fun setProfilePhoto(cover: Boolean, preparePhoto: suspend () -> File) {
        val actor = accountId
        val api = repository
        val cache = timelineCache
        val version = accountGeneration
        _uiState.update { it.copy(operationError = null) }
        viewModelScope.launch {
            try {
                val file = preparePhoto()
                val profile = try {
                    api.profile(actor, api.upload(actor, file), cover)
                } finally {
                    file.delete()
                }
                cache.invalidatePosts(actor)
                if (actor == accountId && version == accountGeneration) applyProfile(actor, profile)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (actor == accountId && version == accountGeneration) _uiState.update { it.copy(operationError = e.message ?: "保存失败，请重试") }
            }
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

/** 为评论、头像与封面准备上传副本；成功文件由调用方在操作结束后清理。 */
internal suspend fun copyMomentPhoto(context: Context, uri: Uri): File = MomentsImagePreparer.prepare(context, uri)
