package plus.rua.project

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 在真实 HTTP 上验证 DTO 映射、请求身份和错误响应，避免只测试假仓库。 */
class MomentsRepositoryTest {
    @Test
    fun resetAvatar_usesDeleteAndCapturedActor_andMapsDefaultWithoutChangingCover() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var actor: String? = null
        var method = ""
        server.createContext("/api/v1/me/avatar") { exchange ->
            actor = exchange.requestHeaders.getFirst("X-Account-ID")
            method = exchange.requestMethod
            val json = """{"id":"xiaobai","name":"小白","avatar_id":null,"cover_id":"cover"}""".toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val profile = HttpMomentsRepository(baseUrl) { "default:$it" }.resetAvatar("xiaobai")
            assertEquals("DELETE", method)
            assertEquals("xiaobai", actor)
            assertEquals("default:xiaobai", profile.avatarPath)
            assertEquals("$baseUrl/api/v1/media/cover?account_id=xiaobai", profile.coverPath)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun accounts_usesRemoteAvatar_andFallsBackToAccountDefault() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/accounts") { exchange ->
            val json = """[{"id":"xiaobai","name":"小白","avatar_id":"custom","cover_id":null},
                {"id":"xiaojimao","name":"小鸡毛","avatar_id":null,"cover_id":null}]""".toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val api = HttpMomentsRepository(baseUrl) { "default:$it" }
            val accounts = api.accounts("xiaojimao")
            assertEquals("$baseUrl/api/v1/media/custom?account_id=xiaojimao", accounts[0].avatarPath)
            assertEquals("default:xiaojimao", accounts[1].avatarPath)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun remotePost_preservesAuthorLikesReplyAndMediaIdentity() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var actor: String? = null
        server.createContext("/api/v1/posts/p") { exchange ->
            actor = exchange.requestHeaders.getFirst("X-Account-ID")
            val json = """{"id":"p","text":"照片🐶","timestamp":123,"author_id":"xiaojimao","author_name":"小鸡毛","avatar_id":"avatar",
                "visibility":"public","location":null,"location_address":null,"photos":[{"id":"photo"}],
                "likes":[{"id":"xiaobai","name":"小白","avatar_id":null}],"comment_count":1,
                "comments":[{"id":"c","author_id":"xiaobai","author_name":"小白","avatar_id":null,"text":"收到", "timestamp":456,
                "reply_to_id":"previous","reply_to_name":"小鸡毛","media_id":"comment-photo","deleted":false}]}""".toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val api = HttpMomentsRepository("http://127.0.0.1:${server.address.port}")
            val post = api.post("xiaobai", "p")
            assertEquals("xiaobai", actor)
            assertEquals("小鸡毛", post.authorName)
            assertTrue(post.isLikedByMe)
            assertTrue(post.photoPaths.single().endsWith("/media/photo?account_id=xiaobai"))
            assertEquals("previous", post.comments.single().replyToId)
            assertFalse(api.post("xiaojimao", "p").isLikedByMe)
            assertEquals("xiaojimao", actor)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun visibility_usesPatchAndCurrentActor_andSurfacesServerError() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var method = ""
        var body = ""
        server.createContext("/api/v1/posts/p") { exchange ->
            method = exchange.requestMethod
            body = exchange.requestBody.readBytes().decodeToString()
            val error = """{"error":"内容不存在或不可见"}""".toByteArray()
            exchange.sendResponseHeaders(404, error.size.toLong())
            exchange.responseBody.use { it.write(error) }
            exchange.close()
        }
        server.start()
        try {
            val api = HttpMomentsRepository("http://127.0.0.1:${server.address.port}")
            val error = runCatching { api.visibility("xiaobai", "p", "私密") }.exceptionOrNull()
            assertTrue(error is MomentsApiException)
            assertEquals(404, error.status)
            assertEquals("PATCH", method)
            assertTrue(body.contains("private"))
        } finally {
            server.stop(0)
        }
    }
}
