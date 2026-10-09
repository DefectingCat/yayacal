package plus.rua.project

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 在真实 HTTP 上验证服务器能力判断，不发送账号头。 */
class ServerProbeTest {
    private fun serve(
        server: String?,
        periodStatus: Int,
        block: suspend (String) -> Unit,
    ) = runTest {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val accountHeaders = mutableListOf<String?>()
        http.createContext("/") { exchange ->
            accountHeaders += exchange.requestHeaders.getFirst("X-Account-ID")
            val status = if (exchange.requestURI.path == "/api/v1/period") periodStatus else 200
            server?.let { exchange.responseHeaders.add("X-Server", it) }
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        http.start()
        try {
            block("http://127.0.0.1:${http.address.port}")
            assertEquals(listOf<String?>(null, null), accountHeaders)
        } finally {
            http.stop(0)
        }
    }

    @Test
    fun probeHttp_currentServer_reportsVersionAndBothCapabilities() = serve("yaya server v0.2.0-a1b2c3d", 200) { url ->
        assertEquals(ServerStatus.Online("0.2.0", moments = true, period = true), HttpServerProbe.probeHttp(url))
    }

    @Test
    fun probeHttp_oldServer_reportsPeriodUnavailable() = serve("yaya server v0.1.2-a1b2c3d", 404) { url ->
        assertEquals(ServerStatus.Online("0.1.2", moments = true, period = false), HttpServerProbe.probeHttp(url))
    }

    @Test
    fun probeHttp_withoutServerHeader_reportsNotYayaServer() = serve(null, 200) { url ->
        assertEquals(ServerStatus.Offline("这个地址不是鸭鸭服务器"), HttpServerProbe.probeHttp(url))
    }

    @Test
    fun probeHttp_closedPort_reportsOffline() = runTest {
        val port = ServerSocket(0).use { it.localPort }

        assertEquals(ServerStatus.Offline("无法连接到服务器"), HttpServerProbe.probeHttp("http://127.0.0.1:$port"))
    }

    @Test
    fun serverVersion_parsesOnlySemanticVersion() {
        assertEquals("0.1.2", serverVersion("yaya server v0.1.2-unknown"))
        assertNull(serverVersion("nginx"))
        assertNull(serverVersion(null))
    }

    @Test
    fun probeErrorMessage_mapsCommonFailures() {
        assertEquals("连接超时，请检查地址或网络", probeErrorMessage(SocketTimeoutException()))
        assertEquals("找不到服务器，请检查地址", probeErrorMessage(UnknownHostException()))
        assertEquals("无法连接到服务器", probeErrorMessage(ConnectException()))
    }
}
