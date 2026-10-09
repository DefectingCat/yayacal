package plus.rua.project

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** 服务器连接状态，工具页服务器卡片据此显示状态灯和能力标签。 */
sealed interface ServerStatus {
    data object Checking : ServerStatus

    /**
     * @property version 后端版本号（如 `0.1.2`），响应头缺失时为 null
     * @property moments 朋友圈接口可用
     * @property period 经期同步接口可用；旧版后端为 false
     */
    data class Online(
        val version: String?,
        val moments: Boolean,
        val period: Boolean,
    ) : ServerStatus

    data class Offline(
        val message: String,
    ) : ServerStatus

    /** 局域网地址需要 Android 17 的本地网络权限，授权前不发起请求。 */
    data object NeedsLocalNetwork : ServerStatus
}

/** 检查某个服务根地址是否可用。 */
fun interface ServerProbe {
    suspend fun probe(url: String): ServerStatus
}

/**
 * 通过 HTTP 检查鸭鸭后端：账号列表判断朋友圈可用，经期接口判断是否已升级；
 * 两个请求都不带账号头，不修改服务器数据。
 */
class HttpServerProbe(
    private val context: Context,
) : ServerProbe {
    override suspend fun probe(url: String): ServerStatus {
        val needsPermission = withContext(Dispatchers.IO) { momentsServiceNeedsLocalNetwork(url, Build.VERSION.SDK_INT) }
        if (needsPermission && !context.hasLocalNetworkPermission()) return ServerStatus.NeedsLocalNetwork
        return probeHttp(url)
    }

    internal companion object {
        private val client: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()

        private data class Answer(
            val code: Int,
            val server: String?,
        )

        internal suspend fun probeHttp(url: String): ServerStatus = try {
            coroutineScope {
                val accounts = async { get("$url/api/v1/accounts") }
                val period = async { get("$url/api/v1/period") }
                val accountsAnswer = accounts.await()
                val periodAnswer = period.await()
                val server = accountsAnswer.server ?: periodAnswer.server
                if (server == null) {
                    ServerStatus.Offline("这个地址不是鸭鸭服务器")
                } else {
                    ServerStatus.Online(
                        version = serverVersion(server),
                        moments = accountsAnswer.code == 200,
                        period = periodAnswer.code == 200,
                    )
                }
            }
        } catch (e: IOException) {
            ServerStatus.Offline(probeErrorMessage(e))
        } catch (_: IllegalArgumentException) {
            ServerStatus.Offline("地址格式不正确")
        }

        private suspend fun get(url: String): Answer = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { Answer(it.code, it.header("X-Server")) }
        }
    }
}

/** 从 `yaya server v0.1.2-a1b2c3d` 取出 `0.1.2`。 */
internal fun serverVersion(header: String?): String? = header?.let { Regex("""v(\d+\.\d+\.\d+)""").find(it)?.groupValues?.get(1) }

internal fun probeErrorMessage(error: IOException): String = when (error) {
    is SocketTimeoutException -> "连接超时，请检查地址或网络"
    is UnknownHostException -> "找不到服务器，请检查地址"
    is SSLException -> "安全连接失败，请确认服务器证书"
    is ConnectException -> "无法连接到服务器"
    else -> "无法连接，请检查地址或网络"
}
