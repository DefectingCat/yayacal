package plus.rua.project

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 在真实 HTTP 上验证 DTO 映射、请求身份和错误响应，避免只测试假仓库。 */
class MomentsRepositoryTest {
    @Test
    fun upload_sizeBoundary_accepts50MiBAndRejectsLargerBeforeRequest() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val sizes = mutableListOf<Long>()
        val actors = mutableListOf<String?>()
        server.createContext("/api/v1/media") { exchange ->
            sizes += exchange.requestBody.use { it.copyTo(OutputStream.nullOutputStream()) }
            actors += exchange.requestHeaders.getFirst("X-Account-ID")
            val json = """{"id":"uploaded"}""".toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        val file = File.createTempFile("moments-upload-test-", ".image")
        server.start()
        try {
            val api = HttpMomentsRepository("http://127.0.0.1:${server.address.port}")
            for (size in listOf(10L * 1024 * 1024 + 1, 50L * 1024 * 1024)) {
                RandomAccessFile(file, "rw").use { it.setLength(size) }
                assertEquals("uploaded", api.upload("xiaojimao", file))
                assertTrue(sizes.last() in (size + 1)..(size + 1024 * 1024))
            }
            RandomAccessFile(file, "rw").use { it.setLength(50L * 1024 * 1024 + 1) }
            assertEquals("图片不能超过 50 MiB", runCatching { api.upload("xiaojimao", file) }.exceptionOrNull()?.message)
            assertEquals(2, sizes.size)
            assertEquals(listOf<String?>("xiaojimao", "xiaojimao"), actors)
        } finally {
            server.stop(0)
            file.delete()
        }
    }

    @Test
    fun profile_usesPatchAndCapturedActor_andReturnsUpdatedProfile() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<Triple<String, String?, String>>()
        server.createContext("/api/v1/me") { exchange ->
            val body = exchange.requestBody.readBytes().decodeToString()
            requests += Triple(exchange.requestMethod, exchange.requestHeaders.getFirst("X-Account-ID"), body)
            val input = JSONObject(body)
            val avatar = input.optString("avatar_id", "old-avatar")
            val cover = input.optString("cover_id", "old-cover")
            val json = """{"id":"xiaobai","name":"小白","avatar_id":"$avatar","cover_id":"$cover"}""".toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val api = HttpMomentsRepository(baseUrl)
            val cover = api.profile("xiaobai", "new-cover", cover = true)
            val avatar = api.profile("xiaobai", "new-avatar", cover = false)

            assertEquals("$baseUrl/api/v1/media/new-cover?account_id=xiaobai", cover.coverPath)
            assertEquals("$baseUrl/api/v1/media/old-avatar?account_id=xiaobai", cover.avatarPath)
            assertEquals("$baseUrl/api/v1/media/new-avatar?account_id=xiaobai", avatar.avatarPath)
            assertEquals("$baseUrl/api/v1/media/old-cover?account_id=xiaobai", avatar.coverPath)
            assertTrue(requests.all { it.first == "PATCH" && it.second == "xiaobai" })
            assertEquals(setOf("cover_id"), JSONObject(requests[0].third).keys().asSequence().toSet())
            assertEquals(setOf("avatar_id"), JSONObject(requests[1].third).keys().asSequence().toSet())
        } finally {
            server.stop(0)
        }
    }

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
    fun remotePost_previewMetadata_preservesReadinessSizesAndCapturedAccount() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/v1/posts/p") { exchange ->
            val json = """{"id":"p","text":"图片","timestamp":123,"author_id":"xiaobai","author_name":"小白","avatar_id":null,
                "visibility":"public","location":null,"location_address":null,
                "photos":[{"id":"ready","bytes":5000000,"preview_bytes":300000},{"id":"legacy","bytes":2000000}],
                "likes":[],"comment_count":1,"comments":[{"id":"c","author_id":"xiaojimao","author_name":"小鸡毛",
                "avatar_id":null,"text":"图片评论","timestamp":456,"reply_to_id":null,"reply_to_name":null,
                "media_id":"comment-photo","media_info":{"bytes":1000000,"preview_bytes":200000},"deleted":false}]}""".toByteArray()
            exchange.sendResponseHeaders(200, json.size.toLong())
            exchange.responseBody.use { it.write(json) }
            exchange.close()
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val api = HttpMomentsRepository(base)
            for (actor in listOf("xiaobai", "xiaojimao")) {
                val post = api.post(actor, "p")
                assertEquals(MomentPhotoMetadata(5_000_000, true), post.photoMetadata["$base/api/v1/media/ready?account_id=$actor"])
                assertEquals(MomentPhotoMetadata(2_000_000, false), post.photoMetadata["$base/api/v1/media/legacy?account_id=$actor"])
                assertEquals(MomentPhotoMetadata(1_000_000, true), post.comments.single().photoMetadata)
                assertTrue(post.photoMetadata.keys.all { it.endsWith("account_id=$actor") })
            }
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
