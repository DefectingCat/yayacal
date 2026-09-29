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

    /** 清空偏好设置（用于测试） */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
