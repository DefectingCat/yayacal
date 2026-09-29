package plus.rua.project

import android.content.Context
import android.content.SharedPreferences

/**
 * 朋友圈偏好设置存储，负责头像路径、相册封面路径与用户名的持久化。
 *
 * @param prefs SharedPreferences 实例
 */
class MomentsStorage(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val PREFS_NAME = "moments_prefs"
        private const val KEY_AVATAR_PATH = "avatar_path"
        private const val KEY_COVER_PATH = "cover_path"
        private const val KEY_USERNAME = "username"
        private const val KEY_POSTS = "moments_posts"
        private const val POSTS_SEPARATOR = "\n"
        const val DEFAULT_USERNAME = "Defectink"

        fun fromContext(context: Context): MomentsStorage {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return MomentsStorage(prefs)
        }
    }

    /** 获取保存的头像绝对路径（未设置时返回 null） */
    fun getAvatarPath(): String? = prefs.getString(KEY_AVATAR_PATH, null)

    /** 保存头像文件绝对路径 */
    fun saveAvatarPath(path: String?) {
        if (path == null) {
            prefs.edit().remove(KEY_AVATAR_PATH).apply()
        } else {
            prefs.edit().putString(KEY_AVATAR_PATH, path).apply()
        }
    }

    /** 获取相册封面绝对路径（未设置时返回 null） */
    fun getCoverPath(): String? = prefs.getString(KEY_COVER_PATH, null)

    /** 保存相册封面文件绝对路径 */
    fun saveCoverPath(path: String?) {
        if (path == null) {
            prefs.edit().remove(KEY_COVER_PATH).apply()
        } else {
            prefs.edit().putString(KEY_COVER_PATH, path).apply()
        }
    }

    /** 获取用户名，默认为 "Defectink" */
    fun getUsername(): String = prefs.getString(KEY_USERNAME, DEFAULT_USERNAME) ?: DEFAULT_USERNAME

    /** 保存用户名 */
    fun saveUsername(name: String) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
    }

    /** 获取保存的朋友圈动态列表，按发布时间倒序排列 */
    fun getPosts(): List<MomentPost> {
        val raw = prefs.getString(KEY_POSTS, null) ?: return emptyList()
        if (raw.isBlank()) return emptyList()
        return raw.split(POSTS_SEPARATOR)
            .mapNotNull { MomentPost.decodeFromString(it) }
            .sortedByDescending { it.timestamp }
    }

    /** 保存一条新的动态（插入到头部） */
    fun savePost(post: MomentPost) {
        val currentPosts = getPosts().toMutableList()
        currentPosts.removeAll { it.id == post.id }
        currentPosts.add(0, post)
        val serialized = currentPosts.joinToString(POSTS_SEPARATOR) { it.encodeToString() }
        prefs.edit().putString(KEY_POSTS, serialized).apply()
    }

    /** 删除指定 ID 的动态 */
    fun deletePost(postId: String) {
        val currentPosts = getPosts().filter { it.id != postId }
        val serialized = currentPosts.joinToString(POSTS_SEPARATOR) { it.encodeToString() }
        prefs.edit().putString(KEY_POSTS, serialized).apply()
    }

    /** 清空所有动态 */
    fun clearPosts() {
        prefs.edit().remove(KEY_POSTS).apply()
    }

    /** 清空偏好设置（用于测试） */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
