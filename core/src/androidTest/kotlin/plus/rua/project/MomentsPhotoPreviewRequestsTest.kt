package plus.rua.project

import android.content.ContentUris
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import plus.rua.project.ui.MomentsPhotoPreviewDialog
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 用模拟器本机 HTTP 和独立测试相册验证真实图片请求，不连接用户的朋友圈服务。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MomentsPhotoPreviewRequestsTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var server: PhotoServer
    private var savedBefore: Set<Long>? = null

    @Before
    fun prepare() {
        if (Build.VERSION.SDK_INT >= 37) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, "android.permission.ACCESS_LOCAL_NETWORK")
        }
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        val bytes = ByteArrayOutputStream().use { output ->
            if (Build.VERSION.SDK_INT >= 30) {
                bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 75, output)
            } else {
                // API 29 尚无 WEBP_LOSSLESS，测试图片使用旧 WebP 编码。
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 100, output)
            }
            output.toByteArray()
        }
        bitmap.recycle()
        server = PhotoServer(bytes)
        savedBefore = savedImages()
    }

    @After
    fun cleanup() {
        if (::server.isInitialized) server.close()
        val previous = savedBefore ?: return
        (savedImages() - previous).forEach { id ->
            context.contentResolver.delete(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), null, null)
        }
    }

    @Test
    fun preview_swipeZoomSaveAndReopen_downloadOriginalOnlyAfterCurrentButtonClick() {
        val photos = listOf(server.photo("first"), server.photo("second"))
        val visible = mutableStateOf(true)
        val initialIndex = mutableStateOf(0)
        server.originalGate = CountDownLatch(1)
        compose.setContent {
            if (visible.value) {
                MomentsPhotoPreviewDialog(
                    photos = photos,
                    initialIndex = initialIndex.value,
                    onDismiss = { visible.value = false },
                    photoMetadata = photos.associateWith { MomentPhotoMetadata(5_000_000, true) },
                )
            }
        }
        compose.waitUntil(10_000) { server.count("preview", "first") > 0 }
        saveCurrentImageAndWait(1)
        assertEquals(0, server.count("original"))
        compose.onNodeWithTag("moments_photo_preview_dialog").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2/2").assertIsDisplayed()
        compose.waitUntil(10_000) { server.count("preview", "second") > 0 }
        compose.onNodeWithTag("moments_photo_preview_dialog").performTouchInput { doubleClick() }
        assertEquals(0, server.count("original"))
        compose.onNodeWithTag("moments_preview_original_button").performClick()
        compose.waitUntil(10_000) { server.count("original", "second") == 1 }
        compose.onNodeWithTag("moments_preview_original_button").assertIsNotEnabled()
        assertEquals(0, server.count("original", "first"))
        server.originalGate.countDown()
        waitForText("已加载原图")
        saveCurrentImageAndWait(2)
        assertEquals(1, server.count("original", "second"))
        compose.runOnUiThread { visible.value = false }
        compose.waitForIdle()
        compose.runOnUiThread {
            initialIndex.value = 1
            visible.value = true
        }
        waitForText("查看原图 · 5.0 MB")
        compose.onNodeWithTag("moments_preview_original_button").performClick()
        waitForText("已加载原图")
        assertEquals(1, server.count("original", "second"))
    }

    @Test
    fun preview_oldServerAndFailedPreview_useThumbnailWithoutOriginalRequest() {
        server.failPreview = true
        val modern = server.photo("modern")
        val legacy = server.photo("legacy")
        val cachedLegacy = server.photo("legacy-cached")
        compose.setContent {
            MomentsPhotoPreviewDialog(
                photos = listOf(modern, legacy, cachedLegacy),
                onDismiss = {},
                photoMetadata = mapOf(modern to MomentPhotoMetadata(1_000_000, true), cachedLegacy to MomentPhotoMetadata(1_000_000, true)),
            )
        }
        compose.waitUntil(10_000) { server.count("thumbnail", "modern") > 0 }
        saveCurrentImageAndWait(1)
        compose.onNodeWithTag("moments_photo_preview_dialog").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2/3").assertIsDisplayed()
        compose.waitUntil(10_000) { server.count("thumbnail", "legacy") > 0 }
        assertEquals(0, server.count("preview", "legacy"))
        compose.onNodeWithTag("moments_photo_preview_dialog").performTouchInput { swipeLeft() }
        compose.onNodeWithText("3/3").assertIsDisplayed()
        compose.waitUntil(10_000) { server.count("thumbnail", "legacy-cached") > 0 }
        assertEquals(0, server.count("original"))
    }

    @Test
    fun original_failedDownload_keepsPreviewAndAllowsExplicitRetry() {
        server.failOriginal = true
        val photo = server.photo("retry")
        compose.setContent {
            MomentsPhotoPreviewDialog(listOf(photo), onDismiss = {}, photoMetadata = mapOf(photo to MomentPhotoMetadata(1_000_000, true)))
        }
        compose.waitUntil(10_000) { server.count("preview", "retry") > 0 }
        compose.onNodeWithTag("moments_preview_original_button").performClick()
        waitForText("加载失败，点击重试")
        assertEquals(1, server.count("original"))
        saveCurrentImageAndWait(1)
        assertEquals(1, server.count("original"))
        server.failOriginal = false
        compose.onNodeWithTag("moments_preview_original_button").performClick()
        waitForText("已加载原图")
        assertEquals(2, server.count("original"))
    }

    @Test
    fun download_withoutOriginal_loadsOriginalThenSavesOnce() {
        val photo = server.photo("download")
        server.originalGate = CountDownLatch(1)
        compose.setContent {
            MomentsPhotoPreviewDialog(listOf(photo), onDismiss = {}, photoMetadata = mapOf(photo to MomentPhotoMetadata(1_000_000, true)))
        }
        compose.waitUntil(10_000) { server.count("preview", "download") > 0 }
        compose.onNodeWithTag("moments_preview_download_button").performClick()
        compose.waitUntil(10_000) { server.count("original", "download") == 1 }
        compose.onNodeWithTag("moments_preview_download_button").assertIsNotEnabled()
        assertEquals(0, (savedImages() - savedBefore.orEmpty()).size)
        server.originalGate.countDown()
        waitForText("已加载原图")
        compose.waitUntil(10_000) { (savedImages() - savedBefore.orEmpty()).size == 1 }
        compose.onNodeWithTag("moments_preview_download_button").performClick()
        compose.waitUntil(10_000) { (savedImages() - savedBefore.orEmpty()).size == 2 }
        assertEquals(1, server.count("original", "download"))
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun saveCurrentImageAndWait(count: Int) {
        compose.onNodeWithTag("moments_photo_preview_dialog").performTouchInput { longClick() }
        compose.onNodeWithText("保存图片").performClick()
        compose.waitUntil(10_000) { (savedImages() - savedBefore.orEmpty()).size == count }
    }

    private fun savedImages(): Set<Long> = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.OWNER_PACKAGE_NAME} = ? AND ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
        arrayOf(context.packageName, "yaya_moments_%"),
        null,
    )?.use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getLong(0)) } }.orEmpty()

    private class PhotoServer(private val bytes: ByteArray) : AutoCloseable {
        private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        private val workers = Executors.newFixedThreadPool(3)
        private val requests = CopyOnWriteArrayList<Pair<String, String>>()

        @Volatile var originalGate = CountDownLatch(0)

        @Volatile var failPreview = false

        @Volatile var failOriginal = false

        init {
            thread(isDaemon = true, name = "moments-photo-http-fixture") {
                while (!socket.isClosed) {
                    try {
                        val connection = socket.accept()
                        workers.execute { serve(connection) }
                    } catch (_: Exception) {
                        if (!socket.isClosed) throw IllegalStateException("图片测试服务意外关闭")
                    }
                }
            }
        }

        fun photo(id: String): String = "http://127.0.0.1:${socket.localPort}/api/v1/media/$id?account_id=xiaobai"

        fun count(variant: String, id: String? = null): Int = requests.count { it.second == variant && (id == null || it.first == id) }

        private fun serve(connection: Socket) {
            connection.use { client ->
                client.soTimeout = 20_000
                val input = client.getInputStream().bufferedReader()
                val line = input.readLine() ?: return
                while (input.readLine()?.isNotEmpty() == true) { /* Consume request headers. */ }
                val uri = URI(line.split(' ')[1])
                val id = uri.path.substringAfterLast('/')
                val variant = when {
                    id.startsWith("legacy") -> if (uri.query.contains("thumbnail=true")) "thumbnail" else "original"
                    uri.query.contains("variant=preview") -> "preview"
                    uri.query.contains("thumbnail=true") -> "thumbnail"
                    else -> "original"
                }
                requests += id to variant
                val failed = (variant == "preview" && failPreview) || (variant == "original" && failOriginal)
                if (variant == "original" && !failed) originalGate.await(20, TimeUnit.SECONDS)
                val body = if (failed) ByteArray(0) else bytes
                val status = if (failed) "404 Not Found" else "200 OK"
                val output = client.getOutputStream()
                output.write("HTTP/1.1 $status\r\nContent-Type: image/webp\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                output.write(body)
                output.flush()
            }
        }

        override fun close() {
            originalGate.countDown()
            socket.close()
            workers.shutdownNow()
        }
    }
}
