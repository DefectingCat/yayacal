package plus.rua.project

import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 仅验证授权范围判断，不依赖 Android 框架或真实 DNS。 */
class MomentsLocalNetworkTest {
    @Test
    fun permission_olderAndroid_skipsResolution() {
        assertFalse(momentsServiceNeedsLocalNetwork("http://10.0.2.2:8088", 36) { error("不应解析") })
    }

    @Test
    fun permission_privateIpv4_requiresAccess() {
        listOf("10.0.2.2", "172.16.0.1", "172.31.255.254", "192.168.7.149", "169.254.1.1").forEach { host ->
            assertTrue(momentsServiceNeedsLocalNetwork("http://$host:8088", 37) { arrayOf(InetAddress.getByName(host)) }, host)
        }
    }

    @Test
    fun permission_publicAndLoopback_doesNotRequireAccess() {
        listOf("1.1.1.1", "172.15.255.254", "172.32.0.1", "127.0.0.1", "127.1.2.3").forEach { host ->
            assertFalse(momentsServiceNeedsLocalNetwork("http://$host:8088", 37) { arrayOf(InetAddress.getByName(host)) }, host)
        }
        assertFalse(momentsServiceNeedsLocalNetwork("http://[::1]:8088", 37) { arrayOf(InetAddress.getByName("::1")) })
    }

    @Test
    fun permission_localIpv6_requiresAccess() {
        listOf("fe80::1", "fd12::1", "fc00::1").forEach { host ->
            assertTrue(momentsServiceNeedsLocalNetwork("https://[$host]", 37) { arrayOf(InetAddress.getByName(host)) }, host)
        }
    }

    @Test
    fun permission_domainResolvingToPrivateAddress_requiresAccess() {
        assertTrue(momentsServiceNeedsLocalNetwork("https://moments.example.com", 37) { arrayOf(InetAddress.getByName("192.168.1.2")) })
        assertFalse(momentsServiceNeedsLocalNetwork("https://moments.example.com", 37) { arrayOf(InetAddress.getByName("1.1.1.1")) })
    }

    @Test
    fun permission_localDomain_doesNotResolveBeforePermission() {
        assertTrue(momentsServiceNeedsLocalNetwork("http://moments.LOCAL.:8088", 37) { error("不应解析") })
    }

    @Test
    fun permission_invalidOrUnresolvableAddress_doesNotAskForPermission() {
        assertFalse(momentsServiceNeedsLocalNetwork("", 37) { error("不应解析") })
        assertFalse(momentsServiceNeedsLocalNetwork("https://missing.example.com", 37) { throw UnknownHostException() })
    }
}
