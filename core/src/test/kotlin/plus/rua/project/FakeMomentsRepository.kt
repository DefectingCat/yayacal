package plus.rua.project

import java.io.File
import java.io.IOException
import java.util.UUID

/** 网络状态测试替身；模拟服务端响应，不使用 Android 框架或真实网络。 */
internal class FakeMomentsRepository : MomentsRepository {
    var posts = listOf<MomentPost>()
    var load: (suspend (String, String?, String?, String?) -> MomentsPage<MomentPost>)? = null
    var failPublish = false
    var publishFailure: Exception? = null
    val attempts = mutableListOf<Triple<String, String, List<String>>>()
    val uploads = mutableListOf<String>()
    val notes = mutableMapOf<String, List<MomentNotification>>()
    val read = mutableListOf<Pair<String, List<String>>>()
    var profiles = listOf(MomentPerson("xiaobai", "小白"), MomentPerson("xiaojimao", "小鸡毛"))
    val avatarResets = mutableListOf<String>()
    var resetAvatarRequest: (suspend (String) -> MomentPerson)? = null
    var commentsRequest: (suspend (String, String, String?) -> MomentsPage<MomentComment>)? = null
    var mutationFailure: Exception? = null
    override suspend fun accounts(actor: String) = profiles
    override suspend fun posts(actor: String, author: String?, query: String?, cursor: String?) = load?.invoke(actor, author, query, cursor) ?: MomentsPage(posts.filter { author == null || it.authorId == author })
    override suspend fun post(actor: String, id: String) = posts.firstOrNull { it.id == id } ?: throw MomentsApiException(404, "不存在")
    override suspend fun publish(actor: String, requestId: String, text: String, media: List<String>, visibility: String, location: String?, address: String?): MomentPost {
        attempts += Triple(actor, requestId, media)
        publishFailure?.let { throw it }
        if (failPublish) throw IOException("网络超时")
        return MomentPost(id = requestId, text = text, authorId = actor).also { posts = posts + it }
    }
    override suspend fun upload(actor: String, file: File): String {
        uploads += actor
        return UUID.randomUUID().toString()
    }
    override suspend fun profile(actor: String, media: String, cover: Boolean) = Unit
    override suspend fun resetAvatar(actor: String): MomentPerson {
        avatarResets += actor
        resetAvatarRequest?.let { return it(actor) }
        val profile = profiles.first { it.id == actor }.copy(avatarPath = null)
        profiles = profiles.map { if (it.id == actor) profile else it }
        return profile
    }
    override suspend fun like(actor: String, id: String, liked: Boolean) {
        mutationFailure?.let { throw it }
        posts = posts.map { if (it.id == id) it.copy(isLikedByMe = liked) else it }
    }
    override suspend fun deletePost(actor: String, id: String) {
        posts = posts.filterNot { it.id == id }
    }
    override suspend fun visibility(actor: String, id: String, visibility: String) {
        posts = posts.map { if (it.id == id) it.copy(visibility = visibility) else it }
    }
    override suspend fun comments(actor: String, id: String, cursor: String?) = commentsRequest?.invoke(actor, id, cursor) ?: MomentsPage(post(actor, id).comments)
    override suspend fun comment(actor: String, id: String, requestId: String, text: String, media: String?, replyTo: String?) = Unit
    override suspend fun deleteComment(actor: String, id: String) = Unit
    override suspend fun notifications(actor: String, cursor: String?) = MomentsPage(notes[actor].orEmpty(), unreadCount = notes[actor].orEmpty().count { !it.isRead })
    override suspend fun readNotifications(actor: String, ids: List<String>) {
        read += actor to ids
    }
    override suspend fun deleteNotification(actor: String, id: String?) {
        notes[actor] = if (id == null) emptyList() else notes[actor].orEmpty().filterNot { it.id == id }
    }
}
