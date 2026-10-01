package plus.rua.project

import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

/**
 * Android 17 的本地网络授权范围。DNS 解析由调用方放到 IO 线程，测试可注入解析结果。
 * 回环地址用于 ADB 转发，不需要本地网络权限；公网服务也不触发授权。
 */
internal fun momentsServiceNeedsLocalNetwork(
    url: String,
    sdkInt: Int,
    resolveHost: (String) -> Array<InetAddress> = InetAddress::getAllByName,
): Boolean {
    if (sdkInt < 37) return false
    val host = runCatching { URI(url).host }.getOrNull()?.removeSurrounding("[", "]")?.trimEnd('.') ?: return false
    if (host.endsWith(".local", ignoreCase = true)) return true
    return try {
        resolveHost(host).any { address ->
            !address.isLoopbackAddress && (
                address.isSiteLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress ||
                    (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)
                )
        }
    } catch (_: UnknownHostException) {
        // 地址解析失败仍交给网络层显示连接错误，不把 DNS 故障误报成缺少权限。
        false
    }
}
