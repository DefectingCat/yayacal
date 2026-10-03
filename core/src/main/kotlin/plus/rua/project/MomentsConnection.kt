package plus.rua.project

import android.content.Context
import android.content.SharedPreferences
import plus.rua.project.shared.BuildConfig
import java.net.URI

/** 按构建环境保存连接地址；历史本地动态保持原样，避免猜测作者后误导入。 */
object MomentsConnection {
    internal const val PREFS = "moments_connection"

    fun url(context: Context): String = settings(context).url()

    fun save(context: Context, url: String) = settings(context).save(url)

    fun repository(context: Context): MomentsRepository = HttpMomentsRepository(url(context)) { id ->
        "android.resource://${context.packageName}/${MomentAccount.findById(id).avatarResId}"
    }

    private fun settings(context: Context) = MomentsConnectionSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
}

/** 构建默认值与环境可注入，便于验证覆盖安装时两个环境的设置不会互相覆盖。 */
internal class MomentsConnectionSettings(
    private val preferences: SharedPreferences,
    private val isDebug: Boolean = BuildConfig.DEBUG,
    private val defaultUrl: String = BuildConfig.MOMENTS_DEFAULT_URL,
) {
    private val urlKey = if (isDebug) DEBUG_URL_KEY else RELEASE_URL_KEY

    fun url(): String {
        migrateLegacyUrl()
        return preferences.getString(urlKey, null)?.let { saved ->
            runCatching { normalizeUrl(saved, allowHttp = isDebug) }.getOrNull()
        } ?: defaultUrl
    }

    fun save(url: String) {
        val normalized = normalizeUrl(url, allowHttp = isDebug)
        preferences.edit().putString(urlKey, normalized).apply()
    }

    private fun migrateLegacyUrl() {
        val legacy = preferences.getString(LEGACY_URL_KEY, null) ?: return
        val debugUrl = runCatching { normalizeUrl(legacy, allowHttp = true) }.getOrNull()?.takeIf { it.startsWith("http://") }
        // 无论哪个构建先启动，都先保留旧 HTTP 调试地址；旧 HTTPS 不再覆盖构建默认值。
        val editor = preferences.edit().remove(LEGACY_URL_KEY)
        if (debugUrl != null && !preferences.contains(DEBUG_URL_KEY)) {
            editor.putString(DEBUG_URL_KEY, debugUrl)
        }
        editor.apply()
    }

    private fun normalizeUrl(url: String, allowHttp: Boolean): String {
        val trimmed = url.trim()
        val uri = URI(trimmed)
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().trim('/').isEmpty()) { "请输入服务地址，例如 https://moments.example.com" }
        require(uri.scheme == "https" || (allowHttp && uri.scheme == "http")) { "服务地址需要使用 HTTPS" }
        return trimmed.trimEnd('/')
    }

    private companion object {
        const val LEGACY_URL_KEY = "url"
        const val DEBUG_URL_KEY = "url_debug"
        const val RELEASE_URL_KEY = "url_release"
    }
}
