package plus.rua.project

/** 按服务连接、查看账号和列表范围隔离的进程内快照，让重新进入朋友圈时先呈现已加载内容。 */
class MomentsTimelineCache {
    private data class Key(val actor: String, val author: String?, val query: String?)

    private val profiles = mutableMapOf<String, List<MomentPerson>>()
    private val timelines = linkedMapOf<Key, MomentsUiState>()

    internal fun restore(actor: String, author: String?, query: String?): MomentsUiState {
        val snapshot = timelines[Key(actor, author, query)]
        val accounts = profiles[actor].orEmpty()
        val profile = accounts.firstOrNull { it.id == (author ?: actor) }
        // 列表只作为非空预览，不能从首页的一页数据推断某个作者没有动态。
        val preview = if (author != null && query == null) {
            timelines[Key(actor, null, null)]?.posts.orEmpty().filter { it.authorId == author }
        } else {
            emptyList()
        }
        return MomentsUiState(
            currentAccountId = actor,
            username = profile?.name ?: MomentAccount.findById(author ?: actor).name,
            avatarPath = profile?.avatarPath,
            coverPath = profile?.coverPath,
            accounts = accounts,
            posts = snapshot?.posts ?: preview,
            nextCursor = snapshot?.nextCursor,
            unreadCount = snapshot?.unreadCount ?: 0,
            searchQuery = query.orEmpty(),
            hasLoadedPosts = snapshot != null,
            isLoading = true,
        )
    }

    internal fun saveProfiles(actor: String, accounts: List<MomentPerson>) {
        profiles[actor] = accounts
    }

    internal fun save(actor: String, author: String?, query: String?, state: MomentsUiState) {
        val key = Key(actor, author, query)
        timelines.remove(key)
        timelines[key] = state
        // 搜索词和分页快照有上限，避免长时间使用不断累积动态与图片地址。
        if (timelines.size > 16) timelines.remove(timelines.keys.first())
    }

    internal fun invalidatePosts(actor: String) {
        timelines.keys.removeAll { it.actor == actor }
    }

    internal fun clear() {
        profiles.clear()
        timelines.clear()
    }

    companion object {
        private val connections = linkedMapOf<String, MomentsTimelineCache>()

        internal fun forConnection(url: String): MomentsTimelineCache = connections.getOrPut(url) {
            if (connections.size >= 4) connections.remove(connections.keys.first())
            MomentsTimelineCache()
        }
    }
}
