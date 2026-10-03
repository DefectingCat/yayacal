package plus.rua.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.max

/** 在后台逐张生成上传副本；成功文件归调用方管理，取消和失败清理未完成的文件。 */
internal object MomentsImagePreparer {
    private val processing = Semaphore(1)
    private val supported = setOf("image/jpeg", "image/png", "image/webp")

    suspend fun prepare(context: Context, uri: Uri): File {
        val directory = context.applicationContext.cacheDir
        var source: File? = null
        var prepared: File? = null
        try {
            return processing.withPermit {
                withContext(Dispatchers.IO) {
                    val copied = File.createTempFile("moment-source-", ".image", directory).also { source = it }
                    val input = context.contentResolver.openInputStream(uri) ?: error("无法读取图片")
                    input.use { copySource(it, copied) }
                    withContext(Dispatchers.Default) {
                        process(copied, directory).also {
                            prepared = it
                            currentCoroutineContext().ensureActive()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            prepared?.delete()
            throw e
        } finally {
            if (source != prepared) source?.delete()
        }
    }

    /** 文件入口供旧草稿补处理；输入文件保持原样，返回新副本或可直接上传的输入。 */
    suspend fun prepareFile(source: File, directory: File): File {
        var prepared: File? = null
        try {
            return processing.withPermit {
                withContext(Dispatchers.Default) {
                    process(source, directory).also {
                        prepared = it
                        currentCoroutineContext().ensureActive()
                    }
                }
            }
        } catch (e: Exception) {
            if (prepared != source) prepared?.delete()
            throw e
        }
    }

    /** 按真实读取量校验大小，不依赖可能缺失或不准确的 ContentProvider 元数据。 */
    internal suspend fun copySource(input: InputStream, target: File) {
        target.outputStream().buffered(64 * 1024).use { output ->
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MomentsImagePolicy.MAX_UPLOAD_BYTES) { MomentsImagePolicy.SIZE_ERROR }
                output.write(buffer, 0, count)
            }
            require(total > 0) { "请选择图片" }
        }
    }

    private suspend fun process(source: File, directory: File): File {
        require(source.length() in 1..MomentsImagePolicy.MAX_UPLOAD_BYTES) { MomentsImagePolicy.SIZE_ERROR }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source.inputStream().use { BitmapFactory.decodeFileDescriptor(it.fd, null, bounds) }
        require(bounds.outMimeType in supported) { "仅支持静态 JPEG、PNG、WebP 图片" }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片损坏或尺寸无效" }
        require(!isAnimated(source, bounds.outMimeType)) { "暂不支持动态图，请选择静态图片" }
        val target = MomentsImagePolicy.targetSize(bounds.outWidth, bounds.outHeight)
        val orientation = try {
            ExifInterface(source).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (e: IOException) {
            throw IOException("图片方向信息损坏", e)
        }
        val isSrgb = Build.VERSION.SDK_INT < 26 || bounds.outColorSpace?.isSrgb != false
        if (source.length() <= MomentsImagePolicy.SMALL_IMAGE_BYTES && target == MomentsImageSize(bounds.outWidth, bounds.outHeight) &&
            orientation in listOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED) && isSrgb
        ) {
            return source
        }

        currentCoroutineContext().ensureActive()
        // 解码器会扫描 PNG 的附加数据；先去掉无关块，方向信息已读取，颜色和透明度相关块保留。
        val png = if (bounds.outMimeType == "image/png") pngForDecode(source, directory) else null
        var bitmap = try {
            decode(png ?: source, bounds, target, orientation)
        } finally {
            png?.delete()
        }
        var output: File? = null
        var success = false
        try {
            output = File.createTempFile("moment-upload-", ".image", directory)
            val lossless = bounds.outMimeType == "image/png" || bitmap.hasAlpha() || MomentsImagePolicy.isLongImage(bitmap.width, bitmap.height)
            val format = format(lossless)
            if (lossless) {
                // 文字、长截图和透明图片优先保真，2 MiB 是目标而非强制限制。
                encode(bitmap, format, 75, output)
            } else {
                for (quality in intArrayOf(85, 80, 75)) {
                    encode(bitmap, format, quality, output)
                    if (output.length() <= MomentsImagePolicy.TARGET_BYTES) break
                }
                repeat(3) {
                    if (output.length() > MomentsImagePolicy.TARGET_BYTES && max(bitmap.width, bitmap.height) > 1280) {
                        val smaller = Bitmap.createScaledBitmap(bitmap, max(1, (bitmap.width * 0.8).toInt()), max(1, (bitmap.height * 0.8).toInt()), true)
                        if (smaller != bitmap) bitmap.recycle()
                        bitmap = smaller
                        encode(bitmap, format, 75, output)
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            require(output.length() in 1..MomentsImagePolicy.MAX_UPLOAD_BYTES) { MomentsImagePolicy.SIZE_ERROR }
            if (output.length() >= source.length() && target == MomentsImageSize(bounds.outWidth, bounds.outHeight) &&
                orientation in listOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED) && isSrgb
            ) {
                return source
            }
            success = true
            return output
        } finally {
            bitmap.recycle()
            if (!success) output?.delete()
        }
    }

    private fun decode(source: File, bounds: BitmapFactory.Options, target: MomentsImageSize, orientation: Int): Bitmap {
        try {
            // 各格式的 EXIF 支持不一致；带方向标记时使用不自动旋转的解码器，再显式校正。
            if (Build.VERSION.SDK_INT >= 28 && orientation in listOf(ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED)) {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                    require(!info.isAnimated) { "暂不支持动态图，请选择静态图片" }
                    val size = MomentsImagePolicy.targetSize(info.size.width, info.size.height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.setTargetSize(size.width, size.height)
                    decoder.setOnPartialImageListener { false }
                }
            }
            return decodeLegacy(source, bounds, target, orientation)
        } catch (e: IOException) {
            throw IOException("图片损坏或无法处理", e)
        }
    }

    /** Android 24–27 的降采样解码，同样覆盖 EXIF 的全部八种旋转与镜像方向。 */
    internal fun decodeLegacy(source: File, bounds: BitmapFactory.Options, target: MomentsImageSize, orientation: Int): Bitmap {
        var sample = 1
        while (bounds.outWidth / sample > target.width * 2 || bounds.outHeight / sample > target.height * 2 ||
            (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > MomentsImagePolicy.MAX_PIXELS * 2
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        var bitmap = source.inputStream().use { BitmapFactory.decodeFileDescriptor(it.fd, null, options) } ?: error("图片损坏或无法处理")
        try {
            val resized = Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
            if (resized != bitmap) bitmap.recycle()
            bitmap = resized
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)

                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)

                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)

                    ExifInterface.ORIENTATION_TRANSPOSE -> {
                        setRotate(90f)
                        postScale(-1f, 1f)
                    }

                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)

                    ExifInterface.ORIENTATION_TRANSVERSE -> {
                        setRotate(-90f)
                        postScale(-1f, 1f)
                    }

                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented != bitmap) bitmap.recycle()
            return oriented
        } catch (e: Exception) {
            bitmap.recycle()
            throw e
        }
    }

    private fun format(lossless: Boolean): Bitmap.CompressFormat = when {
        lossless && Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSLESS
        lossless -> Bitmap.CompressFormat.PNG
        Build.VERSION.SDK_INT >= 30 -> Bitmap.CompressFormat.WEBP_LOSSY
        else -> legacyWebp()
    }

    @Suppress("DEPRECATION") // Android 24–29 没有 WEBP_LOSSY；质量小于 100 使用旧的有损 WebP 编码。
    private fun legacyWebp(): Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP

    private suspend fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, output: File) {
        currentCoroutineContext().ensureActive()
        output.outputStream().buffered(64 * 1024).use { check(bitmap.compress(format, quality, it)) { "图片压缩失败，请重试" } }
        currentCoroutineContext().ensureActive()
    }

    private suspend fun pngForDecode(source: File, directory: File): File {
        val clean = File.createTempFile("moment-decode-", ".png", directory)
        try {
            RandomAccessFile(source, "r").use { input ->
                clean.outputStream().buffered(64 * 1024).use { output ->
                    output.write(ByteArray(8).also { input.readFully(it) })
                    val buffer = ByteArray(64 * 1024)
                    while (input.filePointer + 12 <= input.length()) {
                        currentCoroutineContext().ensureActive()
                        val start = input.filePointer
                        val length = input.readInt().toLong() and 0xffffffffL
                        val kind = ByteArray(4).also { input.readFully(it) }.decodeToString()
                        require(length <= input.length() - input.filePointer - 4) { "图片损坏或尺寸无效" }
                        val end = input.filePointer + length + 4
                        // PNG critical chunks 和像素解释所需的 ancillary chunks 必须原样交给解码器。
                        if (kind.first().isUpperCase() || kind in setOf("tRNS", "iCCP", "sRGB", "gAMA", "cHRM", "sBIT")) {
                            input.seek(start)
                            var remaining = end - start
                            while (remaining > 0) {
                                currentCoroutineContext().ensureActive()
                                val count = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                                require(count > 0) { "图片损坏或尺寸无效" }
                                output.write(buffer, 0, count)
                                remaining -= count
                            }
                        } else {
                            input.seek(end)
                        }
                        if (kind == "IEND") break
                    }
                }
            }
            return clean
        } catch (e: Exception) {
            clean.delete()
            throw e
        }
    }

    /** 在分配 Bitmap 前识别动画；旧版 Android 同样不能静默丢弃后续帧。 */
    internal fun isAnimated(file: File, mime: String): Boolean = RandomAccessFile(file, "r").use { input ->
        if (mime == "image/png") {
            input.seek(8)
            var ended = false
            while (input.filePointer + 12 <= input.length()) {
                val length = input.readInt().toLong() and 0xffffffffL
                val kind = ByteArray(4).also { input.readFully(it) }.decodeToString()
                if (kind == "acTL") return@use true
                if (kind == "IEND") {
                    require(length == 0L) { "图片损坏或尺寸无效" }
                    ended = true
                    break
                }
                require(length <= input.length() - input.filePointer - 4) { "图片损坏或尺寸无效" }
                input.seek(input.filePointer + length + 4)
            }
            require(ended) { "图片损坏或尺寸无效" }
        } else if (mime == "image/webp") {
            input.seek(12)
            while (input.filePointer + 8 <= input.length()) {
                val kind = ByteArray(4).also { input.readFully(it) }.decodeToString()
                val length = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                require(length <= input.length() - input.filePointer) { "图片损坏或尺寸无效" }
                val next = input.filePointer + length + length % 2
                if (kind == "ANIM" || kind == "ANMF") return@use true
                if (kind == "VP8X" && length > 0 && input.readUnsignedByte() and 2 != 0) return@use true
                input.seek(next)
            }
        }
        false
    }
}
