package plus.rua.project

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * [DateRecorderRepository] 照片文件管理与孤立清理功能的单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DateRecorderRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testDir = File("build/tmp/repository_test")

    @BeforeTest
    fun setUp() {
        if (testDir.exists()) testDir.deleteRecursively()
        testDir.mkdirs()
    }

    @AfterTest
    fun tearDown() {
        if (testDir.exists()) testDir.deleteRecursively()
    }

    @Test
    fun createPhotoFile_generatesUniqueFilenames() {
        val dao = TestDao()
        val repository = DateRecorderRepository(dao, testDir, testDispatcher)

        val file1 = repository.createPhotoFile()
        val file2 = repository.createPhotoFile()

        assertNotEquals(file1.name, file2.name)
        assertTrue(file1.name.startsWith("rec_"))
        assertTrue(file1.name.endsWith(".jpg"))
    }

    @Test
    fun update_deletesOldPhotoFile_whenPhotoPathChanges() = runTest(testDispatcher) {
        val dao = TestDao()
        val repository = DateRecorderRepository(dao, testDir, testDispatcher)

        val oldFile =
            File(testDir, "Pictures/date_recorder/old_photo.jpg").apply {
                parentFile?.mkdirs()
                writeText("old photo data")
            }
        val newFile =
            File(testDir, "Pictures/date_recorder/new_photo.jpg").apply {
                parentFile?.mkdirs()
                writeText("new photo data")
            }

        val oldRelPath = repository.relativePathOf(oldFile)
        val newRelPath = repository.relativePathOf(newFile)

        val record =
            DateRecord(
                id = 1L,
                title = "记录",
                note = "",
                shootDate = LocalDate(2026, 7, 23),
                linkedDate = null,
                photoPath = newRelPath,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            )

        repository.update(record, oldPhotoPath = oldRelPath)

        assertFalse(oldFile.exists(), "旧照片文件应该被自动清理")
        assertTrue(newFile.exists(), "新照片文件应该保留")
    }

    @Test
    fun cleanOrphanedPhotos_removesUnreferencedFiles() = runTest(testDispatcher) {
        val activeFile =
            File(testDir, "Pictures/date_recorder/active.jpg").apply {
                parentFile?.mkdirs()
                writeText("active")
            }
        val orphanFile =
            File(testDir, "Pictures/date_recorder/orphan.jpg").apply {
                parentFile?.mkdirs()
                writeText("orphan")
            }

        val activeRelPath = "Pictures/date_recorder/active.jpg"
        val activeRecord =
            DateRecord(
                id = 10L,
                title = "活跃记录",
                note = "",
                shootDate = LocalDate(2026, 7, 23),
                linkedDate = null,
                photoPath = activeRelPath,
                createdAt = Instant.fromEpochMilliseconds(1000L),
            )

        val dao = TestDao(listOf(activeRecord))
        val repository = DateRecorderRepository(dao, testDir, testDispatcher)

        val cleanedCount = repository.cleanOrphanedPhotos()

        assertEquals(1, cleanedCount)
        assertTrue(activeFile.exists(), "数据库引用的照片不应被删除")
        assertFalse(orphanFile.exists(), "未引用的孤立照片应被清理")
    }

    @Test
    fun cleanOrphanedPhotos_preservesTmpCameraFiles() = runTest(testDispatcher) {
        // tmp_ 前缀的文件是相机拍摄中的临时文件，尚未写入数据库记录，不应被清理
        val tmpFile =
            File(testDir, "Pictures/date_recorder/tmp_1234567890.jpg").apply {
                parentFile?.mkdirs()
                writeText("camera capture in progress")
            }
        val orphanFile =
            File(testDir, "Pictures/date_recorder/orphan.jpg").apply {
                parentFile?.mkdirs()
                writeText("orphan")
            }

        val dao = TestDao()
        val repository = DateRecorderRepository(dao, testDir, testDispatcher)

        val cleanedCount = repository.cleanOrphanedPhotos()

        assertEquals(1, cleanedCount, "仅孤立文件被清理")
        assertTrue(tmpFile.exists(), "tmp_ 前缀的临时文件不应被删除")
        assertFalse(orphanFile.exists(), "孤立文件应被清理")
    }

    private class TestDao(
        initialRecords: List<DateRecord> = emptyList(),
    ) : DateRecordDao {
        val recordsFlow = MutableStateFlow(initialRecords)

        override fun getAllFlow(): Flow<List<DateRecord>> = recordsFlow

        override fun getByIdFlow(id: Long): Flow<DateRecord?> = error("Not implemented")

        override suspend fun insert(record: DateRecord): Long = 1L

        override suspend fun update(record: DateRecord) {}

        override suspend fun deleteByIds(ids: List<Long>): Int = ids.size
    }
}
