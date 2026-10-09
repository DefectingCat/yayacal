package plus.rua.project

import android.content.Context
import android.content.SharedPreferences
import plus.rua.project.shared.BuildConfig
import java.net.URI

/** 服务器设置里的快捷地址。 */
data class ServerPreset(
    val label: String,
    val url: String,
)

/**
 * 按构建环境保存服务器地址，朋友圈与经期记录共用；在工具页的服务器设置中修改。
 * 历史本地动态保持原样，避免猜测作者后误导入。
 */
object MomentsConnection {
    internal const val PREFS = "moments_connection"

    fun url(context: Context): String = settings(context).url()

    fun save(context: Context, url: String) = settings(context).save(url)

    fun repository(context: Context): MomentsRepository = HttpMomentsRepository(url(context)) { id ->
        "android.resource://${context.packageName}/${MomentAccount.findById(id).avatarResId}"
    }

    internal fun settings(context: Context) = MomentsConnectionSettings(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))
}

/** 构建默认值与环境可注入，便于验证覆盖安装时两个环境的设置不会互相覆盖。 */
internal class MomentsConnectionSettings(
    private val preferences: SharedPreferences,
    private val isDebug: Boolean = BuildConfig.DEBUG,
    val defaultUrl: String = BuildConfig.MOMENTS_DEFAULT_URL,
    private val onlineUrl: String = BuildConfig.MOMENTS_ONLINE_URL,
) {
    private val urlKey = if (isDebug) DEBUG_URL_KEY else RELEASE_URL_KEY

    fun url(): String {
        migrateLegacyUrl()
        return preferences.getString(urlKey, null)?.let { saved ->
            runCatching { normalizeUrl(saved, allowHttp = isDebug) }.getOrNull()
        } ?: defaultUrl
    }

    /** 保存并返回规范化后的地址；格式不符合当前构建要求时抛出带中文说明的 [IllegalArgumentException]。 */
    fun save(url: String): String {
        val normalized = normalize(url)
        preferences.edit().putString(urlKey, normalized).apply()
        return normalized
    }

    /** 只校验不保存，供「测试连接」使用。 */
    fun normalize(url: String): String = normalizeUrl(url, allowHttp = isDebug)

    /** Debug 额外提供 ADB 转发与线上地址，Release 只有默认地址。 */
    fun presets(): List<ServerPreset> {
        val presets =
            if (isDebug) {
                listOf(
                    ServerPreset("模拟器默认", defaultUrl),
                    ServerPreset("ADB 转发", ADB_URL),
                    ServerPreset("线上", onlineUrl),
                )
            } else {
                listOf(ServerPreset("默认", defaultUrl))
            }
        return presets.distinctBy { it.url }
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
        val uri = runCatching { URI(trimmed) }.getOrElse { throw IllegalArgumentException("地址格式不正确，例如 https://yaya.example.com") }
        require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null && uri.path.orEmpty().trim('/').isEmpty()) { "请输入服务根地址，例如 https://yaya.example.com" }
        require(uri.scheme == "https" || (allowHttp && uri.scheme == "http")) { "服务地址需要使用 HTTPS" }
        return trimmed.trimEnd('/')
    }

    private companion object {
        const val LEGACY_URL_KEY = "url"
        const val DEBUG_URL_KEY = "url_debug"
        const val RELEASE_URL_KEY = "url_release"
        const val ADB_URL = "http://127.0.0.1:8088"
    }
}
