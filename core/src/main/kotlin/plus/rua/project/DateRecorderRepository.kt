package plus.rua.project

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 日期记录器的数据仓库，封装 DAO 访问与照片文件管理。
 *
 * 照片统一存放在 `filesDir/Pictures/date_recorder/`，数据库中保存相对路径，
 * 删除记录时同步删除对应照片文件。
 *
 * @param dao 记录 DAO
 * @param filesDir 应用 filesDir，照片根目录
 */
class DateRecorderRepository(
    private val dao: DateRecordDao,
    private val filesDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /** 照片存放的子目录（相对 filesDir） */
    private val photoDir: File by lazy {
        File(filesDir, PHOTO_DIR_NAME).apply { if (!exists()) mkdirs() }
    }

    /**
     * 订阅全部记录，UI 层据此渲染相册网格。
     */
    fun observeAll(): Flow<List<DateRecord>> = dao.getAllFlow()

    /**
     * 订阅单条记录，用于详情页。
     *
     * @param id 记录主键
     */
    fun observeById(id: Long): Flow<DateRecord?> = dao.getByIdFlow(id)

    /**
     * 新增一条记录。
     *
     * @param record 待插入记录，id 由 DB 自增
     * @return 新记录的 ID
     */
    suspend fun insert(record: DateRecord): Long = dao.insert(record)

    /**
     * 更新一条记录。编辑信息/替换照片后调用。若传入 oldPhotoPath 且与新照片路径不同，自动清理旧照片文件。
     */
    suspend fun update(record: DateRecord, oldPhotoPath: String? = null) = withContext(ioDispatcher) {
        dao.update(record)
        if (oldPhotoPath != null && oldPhotoPath != record.photoPath) {
            deletePhotoFile(oldPhotoPath)
        }
    }

    /**
     * 按 ID 批量删除记录，并同步删除对应照片文件。
     *
     * @param records 待删除记录列表（需要各自的 photoPath 来清理文件）
     */
    suspend fun deleteWithPhotos(records: List<DateRecord>) = withContext(ioDispatcher) {
        records.forEach { deletePhotoFile(it.photoPath) }
        dao.deleteByIds(records.map { it.id })
    }

    /**
     * 删除单条记录及其照片文件。
     *
     * @param record 待删除记录（需要其 photoPath 来清理文件）
     */
    suspend fun deleteWithPhoto(record: DateRecord) = withContext(ioDispatcher) {
        deletePhotoFile(record.photoPath)
        dao.deleteByIds(listOf(record.id))
    }

    /**
     * 生成一个新的照片文件（不含扩展名），调用方写入内容后回传相对路径。
     *
     * @return 照片文件，路径相对 filesDir
     */
    fun createPhotoFile(): File {
        val timestamp = System.currentTimeMillis()
        val nonce = (1000..9999).random()
        val name = "rec_${timestamp}_${nonce}"
        return File(photoDir, "$name.jpg")
    }

    /**
     * 将照片文件的绝对路径转换为相对 filesDir 的路径，用于 DB 持久化。
     */
    fun relativePathOf(file: File): String = file.absolutePath
        .removePrefix(filesDir.absolutePath)
        .removePrefix(File.separator)

    /**
     * 将 DB 中的相对路径转换为绝对路径 File。
     */
    fun absoluteFileOf(relativePath: String): File = File(filesDir, relativePath)

    /**
     * 删除指定相对路径的照片文件，文件不存在时静默忽略。
     */
    internal fun deletePhotoFile(relativePath: String) {
        runCatching { absoluteFileOf(relativePath).delete() }
    }
    /**
     * 扫描照片目录并清理无任何数据库记录引用的孤立照片文件。
     * @return 清理的孤立文件数量
     */
    suspend fun cleanOrphanedPhotos(): Int = withContext(ioDispatcher) {
        val activePhotos = dao.getAllFlow().first().map { it.photoPath }.toSet()
        var deletedCount = 0
        photoDir.listFiles()?.forEach { file ->
            val relPath = relativePathOf(file)
            if (relPath !in activePhotos) {
                if (file.delete()) {
                    deletedCount++
                }
            }
        }
        deletedCount
    }

    companion object {
        const val PHOTO_DIR_NAME = "Pictures/date_recorder"

        /**
         * 从 Context 构造 Repository，内部自建 DAO。
         */
        fun fromContext(context: Context): DateRecorderRepository {
            val db = DateRecordDatabase.fromContext(context)
            return DateRecorderRepository(db.dateRecordDao(), context.filesDir)
        }
    }
}
