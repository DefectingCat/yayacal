package plus.rua.project

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.CRC32

/** 在独立测试 APK 中验证真实 Android 解码、透明度、方向与 ContentResolver 读取边界。 */
@RunWith(AndroidJUnit4::class)
class MomentsImagePreparerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var directory: File

    @Before fun before() {
        directory = File.createTempFile("moments-image-test-", "", context.cacheDir).apply {
            delete()
            mkdirs()
        }
    }

    @After fun after() {
        directory.deleteRecursively()
    }

    @Test fun prepare_largeJpeg_reducesUploadBytesAndKeepsOriginal() = runBlocking {
        val source = image(4000, 3000, Bitmap.CompressFormat.JPEG) { bitmap ->
            var seed = 17
            val row = IntArray(bitmap.width)
            for (y in 0 until bitmap.height) {
                for (x in row.indices) {
                    seed = seed * 1664525 + 1013904223
                    row[x] = seed or (0xff shl 24)
                }
                bitmap.setPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            }
        }
        val originalHash = hash(source)
        val output = MomentsImagePreparer.prepareFile(source, directory)
        val size = bounds(output)
        assertTrue(size.outWidth <= 2560 && size.outHeight <= 2560)
        assertTrue(output.length() < source.length())
        assertTrue(output.length() <= 2L * 1024 * 1024)
        assertEquals(originalHash, hash(source))
        println("Image compression: ${source.length()} -> ${output.length()} bytes, ${size.outWidth}x${size.outHeight}")
    }

    @Test fun prepare_smallPhoto_reusesInputWithoutChangingIt() = runBlocking {
        val source = image(320, 240, Bitmap.CompressFormat.JPEG) { it.eraseColor(Color.GREEN) }
        assertEquals(source, MomentsImagePreparer.prepareFile(source, directory))
    }

    @Test fun prepare_transparentPng_preservesAlpha() = runBlocking {
        val source = paddedPng(
            image(64, 64) {
                it.eraseColor(Color.TRANSPARENT)
                it.setPixel(1, 0, Color.argb(128, 0, 255, 0))
            },
            2 * 1024 * 1024,
        )
        val output = MomentsImagePreparer.prepareFile(source, directory)
        val bitmap = BitmapFactory.decodeFile(output.absolutePath)
        try {
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            assertEquals(128, Color.alpha(bitmap.getPixel(1, 0)))
            assertEquals(255, Color.green(bitmap.getPixel(1, 0)))
            assertTrue(output.length() < source.length())
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun prepare_longScreenshot_preservesUsefulWidthAndLength() = runBlocking {
        val source = image(1440, 10000) {
            it.eraseColor(Color.WHITE)
            val canvas = Canvas(it)
            val paint = Paint().apply {
                color = Color.BLACK
                textSize = 44f
                isAntiAlias = true
            }
            for (y in 80..9900 step 80) canvas.drawText("朋友圈长截图：这一行文字应该保持清晰可读。", 20f, y.toFloat(), paint)
        }
        val output = MomentsImagePreparer.prepareFile(source, directory)
        val size = bounds(output)
        assertTrue(size.outWidth in 1000..1080)
        assertTrue(size.outHeight > 7000)
        assertTrue(size.outWidth.toLong() * size.outHeight <= 8_000_000)
    }

    @Test fun prepare_exifOrientations_modernAndLegacyPreserveAllEightDirections() = runBlocking {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
        val corners = intArrayOf(Color.RED, Color.BLUE, Color.MAGENTA, Color.YELLOW, Color.RED, Color.YELLOW, Color.MAGENTA, Color.BLUE)
        for (orientation in 1..8) {
            val source = image(3, 2) { it.setPixels(colors, 0, 3, 0, 0, 3, 2) }
            ExifInterface(source).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setLatLong(31.2, 121.5)
                saveAttributes()
            }
            val output = MomentsImagePreparer.prepareFile(source, directory)
            val modern = BitmapFactory.decodeFile(output.absolutePath)
            val info = bounds(source)
            val legacy = MomentsImagePreparer.decodeLegacy(source, info, MomentsImageSize(3, 2), orientation)
            try {
                val expectedWidth = if (orientation >= 5) 2 else 3
                val expectedHeight = if (orientation >= 5) 3 else 2
                for ((decoder, bitmap) in listOf(modern, legacy).withIndex()) {
                    assertEquals("decoder=$decoder orientation=$orientation", expectedWidth, bitmap.width)
                    assertEquals("decoder=$decoder orientation=$orientation", expectedHeight, bitmap.height)
                    assertEquals("decoder=$decoder orientation=$orientation", corners[orientation - 1], bitmap.getPixel(0, 0))
                }
                if (orientation != 1) {
                    assertFalse(ExifInterface(output).hasAttribute(ExifInterface.TAG_GPS_LATITUDE))
                    assertTrue(ExifInterface(output).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) in listOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED))
                }
                assertEquals(orientation, ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
            } finally {
                modern.recycle()
                legacy.recycle()
            }
        }
    }

    @Test fun prepare_animatedWebp_reportsUnsupportedAndLeavesNoPartialOutput() = runBlocking {
        val source = File(directory, "animated.webp")
        context.assets.open("animations/001.webp").use { input -> source.outputStream().use { input.copyTo(it) } }
        val files = directory.list()!!.toSet()
        val error = runCatching { MomentsImagePreparer.prepareFile(source, directory) }.exceptionOrNull()
        assertEquals("暂不支持动态图，请选择静态图片", error?.message)
        assertEquals(files, directory.list()!!.toSet())
    }

    @Test fun prepare_truncatedPng_reportsDamagedBeforeEncoding() = runBlocking {
        val source = image(2, 2) { it.eraseColor(Color.RED) }
        RandomAccessFile(source, "rw").use { it.setLength(it.length() - 12) }
        val error = runCatching { MomentsImagePreparer.prepareFile(source, directory) }.exceptionOrNull()
        assertEquals("图片损坏或尺寸无效", error?.message)
    }

    @Test fun prepare_uriAt50MiB_succeedsAndOverLimitCleansTemporaryCopy() = runBlocking {
        var started = System.nanoTime()
        val source = paddedPng(image(2, 2) { it.eraseColor(Color.RED) }, 50 * 1024 * 1024)
        println("50 MiB fixture creation: ${(System.nanoTime() - started) / 1_000_000} ms")
        started = System.nanoTime()
        val output = MomentsImagePreparer.prepare(context, Uri.fromFile(source))
        println("50 MiB prepare: ${(System.nanoTime() - started) / 1_000_000} ms")
        try {
            assertTrue(output.length() < 1024)
            assertEquals(2, bounds(output).outWidth)
        } finally {
            output.delete()
        }
        val oversized = File(directory, "oversized.image")
        RandomAccessFile(oversized, "rw").use { it.setLength(50L * 1024 * 1024 + 1) }
        val temporaryFiles = context.cacheDir.list()!!.filter { it.startsWith("moment-") }.toSet()
        started = System.nanoTime()
        val error = runCatching { MomentsImagePreparer.prepare(context, Uri.fromFile(oversized)) }.exceptionOrNull()
        println("50 MiB reject: ${(System.nanoTime() - started) / 1_000_000} ms")
        assertEquals("图片不能超过 50 MiB", error?.message)
        assertEquals(temporaryFiles, context.cacheDir.list()!!.filter { it.startsWith("moment-") }.toSet())
    }

    private fun image(width: Int, height: Int, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, draw: (Bitmap) -> Unit): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            draw(bitmap)
            File.createTempFile("source-", ".image", directory).apply {
                outputStream().use { assertTrue(bitmap.compress(format, 95, it)) }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun paddedPng(source: File, size: Int): File {
        val data = source.readBytes()
        val padding = size - data.size - 12
        assertTrue(padding >= 0)
        val output = File.createTempFile("padded-", ".png", directory)
        DataOutputStream(output.outputStream().buffered(64 * 1024)).use {
            it.write(data, 0, data.size - 12)
            it.writeInt(padding)
            val kind = "paDd".toByteArray()
            it.write(kind)
            val crc = CRC32().apply { update(kind) }
            val buffer = ByteArray(8192)
            var remaining = padding
            while (remaining > 0) {
                val count = minOf(remaining, buffer.size)
                it.write(buffer, 0, count)
                crc.update(buffer, 0, count)
                remaining -= count
            }
            it.writeInt(crc.value.toInt())
            it.write(data, data.size - 12, 12)
        }
        assertEquals(size.toLong(), output.length())
        return output
    }

    private fun bounds(file: File) = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
