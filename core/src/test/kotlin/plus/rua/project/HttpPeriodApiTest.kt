package plus.rua.project

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.json.JSONObject
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 在真实 HTTP 上验证经期文档的字段映射、请求头和错误响应。 */
class HttpPeriodApiTest {
    private val documentJson =
        """
        {"revision":7,"settings":{"cycle_length":30,"period_length":6,"luteal_length":13},
         "ranges":[{"start":"2026-09-12","end":"2026-09-16"},{"start":"2026-10-08","end":null}],
         "notes":[{"date":"2026-10-08","mood":"tired","text":"腰酸"},{"date":"2026-10-09","mood":null,"text":"热水袋"}]}
        """.trimIndent()

    private val document =
        PeriodDocument(
            revision = 7,
            settings = PeriodSettings(30, 6, 13),
            ranges = listOf(PeriodRange(LocalDate(2026, 9, 12), LocalDate(2026, 9, 16)), PeriodRange(LocalDate(2026, 10, 8), null)),
            notes = listOf(PeriodNote(LocalDate(2026, 10, 8), PeriodMood.TIRED, "腰酸"), PeriodNote(LocalDate(2026, 10, 9), null, "热水袋")),
        )

    @Test
    fun fetchAndReplace_mapFieldsAndOmitAccountHeader() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<Triple<String, String?, String>>()
        server.createContext("/api/v1/period") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            requests += Triple(exchange.requestMethod, exchange.requestHeaders.getFirst("X-Account-ID"), body)
            val json = documentJson.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val api = HttpPeriodApi("http://127.0.0.1:${server.address.port}")

            assertEquals(document, api.fetch())
            assertEquals(document, api.replace(document))

            assertEquals(listOf("GET", "PUT"), requests.map { it.first })
            assertNull(requests[0].second)
            assertNull(requests[1].second)
            val sent = JSONObject(requests[1].third)
            assertEquals(setOf("base_revision", "settings", "ranges", "notes"), sent.keys().asSequence().toSet())
            assertEquals(7, sent.getLong("base_revision"))
            assertEquals(JSONObject.NULL, sent.getJSONArray("ranges").getJSONObject(1).get("end"))
            assertEquals("tired", sent.getJSONArray("notes").getJSONObject(0).getString("mood"))
            assertEquals(JSONObject.NULL, sent.getJSONArray("notes").getJSONObject(1).get("mood"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun replace_conflict_throwsApiExceptionWithServerMessage() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/period") { exchange ->
            exchange.requestBody.readBytes()
            val json = """{"error":"经期记录已在其他设备更新"}""".toByteArray()
            exchange.sendResponseHeaders(409, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val error = runCatching { HttpPeriodApi("http://127.0.0.1:${server.address.port}").replace(document) }.exceptionOrNull()

            assertEquals(409, (error as PeriodApiException).status)
            assertEquals("经期记录已在其他设备更新", error.message)
        } finally {
            server.stop(0)
        }
    }
}
