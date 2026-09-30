package plus.rua.project

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import plus.rua.project.shared.BuildConfig
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** 一页服务端结果，游标必须原样传回。 */
data class MomentsPage<T>(val items: List<T>, val nextCursor: String? = null, val unreadCount: Int = 0)

/** 请求中的账号是操作开始时捕获的身份，不能在网络请求过程中重新读取全局选中账号。 */
interface MomentsRepository {
    suspend fun accounts(actor: String): List<MomentPerson>
    suspend fun posts(actor: String, author: String? = null, query: String? = null, cursor: String? = null): MomentsPage<MomentPost>
    suspend fun post(actor: String, id: String): MomentPost
    suspend fun publish(actor: String, requestId: String, text: String, media: List<String>, visibility: String, location: String?, address: String?): MomentPost
    suspend fun upload(actor: String, file: File): String
    suspend fun profile(actor: String, media: String, cover: Boolean)
    suspend fun like(actor: String, id: String, liked: Boolean)
    suspend fun deletePost(actor: String, id: String)
    suspend fun visibility(actor: String, id: String, visibility: String)
    suspend fun comments(actor: String, id: String, cursor: String? = null): MomentsPage<MomentComment>
    suspend fun comment(actor: String, id: String, requestId: String, text: String, media: String?, replyTo: String?)
    suspend fun deleteComment(actor: String, id: String)
    suspend fun notifications(actor: String, cursor: String? = null): MomentsPage<MomentNotification>
    suspend fun readNotifications(actor: String, ids: List<String>)
    suspend fun deleteNotification(actor: String, id: String?)
}

/** HTTP 状态可用于区分已删除内容与暂时断网，界面只显示面向用户的说明。 */
class MomentsApiException(val status: Int, message: String) : IOException(message)

/** 连接地址与选中身份属于本机偏好，历史本地动态保持原样，避免猜测作者后误导入。 */
object MomentsConnection {
    private const val PREFS = "moments_connection"
    fun url(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("url", if (BuildConfig.DEBUG) "http://10.0.2.2:8088" else "")!!.trimEnd('/')

    fun save(context: Context, url: String) {
        val uri = java.net.URI(url.trim())
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().trim('/').isEmpty()) { "请输入服务地址，例如 https://moments.example.com" }
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http")) { "服务地址需要使用 HTTPS" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("url", url.trim().trimEnd('/')).apply()
    }

    fun repository(context: Context): MomentsRepository = HttpMomentsRepository(url(context)) { id ->
        "android.resource://${context.packageName}/${MomentAccount.findById(id).avatarResId}"
    }
}

/** HTTP 实现把上传、JSON 映射与错误统一收在一个位置，所有业务请求都有显式账号。 */
class HttpMomentsRepository(
    private val baseUrl: String,
    private val defaultAvatar: (String) -> String? = { null },
) : MomentsRepository {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).followRedirects(false).build()

    private suspend fun request(actor: String, method: String, path: String, body: RequestBody? = null): String = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank()) throw IOException("请先在账号选择页设置朋友圈服务地址")
        val request = Request.Builder().url("$baseUrl/api/v1$path").header("X-Account-ID", actor).method(method, body).build()
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("连接失败，请检查网络或服务地址", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) throw MomentsApiException(it.code, runCatching { JSONObject(text).getString("error") }.getOrDefault("请求失败（${it.code}）"))
            text
        }
    }

    private fun body(vararg fields: Pair<String, Any?>): RequestBody = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, if (value is List<*>) JSONArray(value) else value ?: JSONObject.NULL) }
    }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

    private fun media(id: String?, actor: String): String? = id?.let { "$baseUrl/api/v1/media/$it?account_id=$actor" }
    private fun person(json: JSONObject, actor: String): MomentPerson = MomentPerson(
        json.getString("id"),
        json.getString("name"),
        media(json.optional("avatar_id"), actor) ?: defaultAvatar(json.getString("id")),
        media(json.optional("cover_id"), actor),
    )
    private fun comment(json: JSONObject, actor: String) = MomentComment(
        id = json.getString("id"), authorName = json.getString("author_name"), text = if (json.optBoolean("deleted")) "评论已删除" else json.getString("text"),
        timestamp = json.getLong("timestamp"), replyToName = json.optional("reply_to_name"), photoPath = media(json.optional("media_id"), actor),
        authorId = json.getString("author_id"), authorAvatarPath = media(json.optional("avatar_id"), actor) ?: defaultAvatar(json.getString("author_id")),
        replyToId = json.optional("reply_to_id"), deleted = json.optBoolean("deleted"),
    )
    private fun post(json: JSONObject, actor: String): MomentPost {
        val likes = json.getJSONArray("likes").objects().map { person(it, actor) }
        return MomentPost(
            id = json.getString("id"), text = json.getString("text"), timestamp = json.getLong("timestamp"),
            photoPaths = json.getJSONArray("photos").objects().mapNotNull { media(it.getString("id"), actor) },
            location = json.optional("location"), locationAddress = json.optional("location_address"),
            visibility = if (json.getString("visibility") == "private") "私密" else "公开", isLikedByMe = likes.any { it.id == actor },
            comments = json.getJSONArray("comments").objects().map { comment(it, actor) }, authorId = json.getString("author_id"), authorName = json.getString("author_name"),
            authorAvatarPath = media(json.optional("avatar_id"), actor) ?: defaultAvatar(json.getString("author_id")), likes = likes, commentCount = json.getInt("comment_count"),
        )
    }
    private fun query(vararg fields: Pair<String, String?>): String = fields.filter { it.second != null }.joinToString("&", prefix = "?") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
    override suspend fun accounts(actor: String): List<MomentPerson> = JSONArray(request(actor, "GET", "/accounts")).objects().map { person(it, actor) }
    override suspend fun posts(actor: String, author: String?, query: String?, cursor: String?): MomentsPage<MomentPost> {
        val json = JSONObject(request(actor, "GET", "/posts" + query("author_id" to author, "q" to query, "cursor" to cursor)))
        return MomentsPage(json.getJSONArray("items").objects().map { post(it, actor) }, json.optional("next_cursor"))
    }
    override suspend fun post(actor: String, id: String): MomentPost = post(JSONObject(request(actor, "GET", "/posts/$id")), actor)
    override suspend fun publish(actor: String, requestId: String, text: String, media: List<String>, visibility: String, location: String?, address: String?): MomentPost = post(
        JSONObject(request(actor, "POST", "/posts", body("request_id" to requestId, "text" to text, "media_ids" to media, "visibility" to visibilityValue(visibility), "location" to location, "location_address" to address))),
        actor,
    )
    override suspend fun upload(actor: String, file: File): String {
        require(file.length() in 1..(10L * 1024 * 1024)) { "图片不能超过 10 MiB" }
        val payload = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", "photo", file.asRequestBody("application/octet-stream".toMediaType())).build()
        return JSONObject(request(actor, "POST", "/media", payload)).getString("id")
    }
    override suspend fun profile(actor: String, media: String, cover: Boolean) {
        request(actor, "PATCH", "/me", body((if (cover) "cover_id" else "avatar_id") to media))
    }
    override suspend fun like(actor: String, id: String, liked: Boolean) {
        request(actor, if (liked) "PUT" else "DELETE", "/posts/$id/like", if (liked) "".toRequestBody() else null)
    }
    override suspend fun deletePost(actor: String, id: String) {
        request(actor, "DELETE", "/posts/$id")
    }
    override suspend fun visibility(actor: String, id: String, visibility: String) {
        request(actor, "PATCH", "/posts/$id", body("visibility" to visibilityValue(visibility)))
    }
    override suspend fun comments(actor: String, id: String, cursor: String?): MomentsPage<MomentComment> {
        val json = JSONObject(request(actor, "GET", "/posts/$id/comments" + query("cursor" to cursor, "limit" to "50")))
        return MomentsPage(json.getJSONArray("items").objects().map { comment(it, actor) }, json.optional("next_cursor"))
    }
    override suspend fun comment(actor: String, id: String, requestId: String, text: String, media: String?, replyTo: String?) {
        request(actor, "POST", "/posts/$id/comments", body("request_id" to requestId, "text" to text, "media_id" to media, "reply_to_id" to replyTo))
    }
    override suspend fun deleteComment(actor: String, id: String) {
        request(actor, "DELETE", "/comments/$id")
    }
    override suspend fun notifications(actor: String, cursor: String?): MomentsPage<MomentNotification> {
        val json = JSONObject(request(actor, "GET", "/notifications" + query("cursor" to cursor)))
        return MomentsPage(
            json.getJSONArray("items").objects().map {
                MomentNotification(
                    id = it.getString("id"), postId = it.getString("post_id"), authorName = it.getString("author_name"),
                    authorAvatar = media(it.optional("avatar_id"), actor) ?: defaultAvatar(it.getString("author_id")), type = MomentNotificationType.valueOf(it.getString("type").uppercase()),
                    content = it.optional("content"), timestamp = it.getLong("timestamp"), postPhotoPath = media(it.optional("post_media_id"), actor), postText = it.getString("post_text"), isRead = it.getBoolean("read"),
                )
            },
            json.optional("next_cursor"),
            json.getInt("unread_count"),
        )
    }
    override suspend fun readNotifications(actor: String, ids: List<String>) {
        request(actor, "POST", "/notifications/read", body("ids" to ids))
    }
    override suspend fun deleteNotification(actor: String, id: String?) {
        request(actor, "DELETE", "/notifications" + (id?.let { "/$it" } ?: ""))
    }
}

private fun JSONObject.optional(key: String): String? = if (isNull(key)) null else getString(key)
private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun visibilityValue(value: String): String = if (value == "私密") "private" else "public"
