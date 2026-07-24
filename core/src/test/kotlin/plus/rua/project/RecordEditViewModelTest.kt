package plus.rua.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * 新建记录标题预填与拍摄日期联动的单元测试。
 *
 * 新建模式的被测路径不会触发 DAO 调用，[FakeDateRecordDao] 返回空数据即可。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordEditViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): RecordEditViewModel {
        val repository = DateRecorderRepository(FakeDateRecordDao(), File("build/tmp/record_edit_test"))
        return RecordEditViewModel(repository, photoPath = "/tmp/fake_photo.jpg", recordId = null)
    }

    @Test
    fun newRecord_prefillsTitleWithShootDate_andCanSave() {
        val vm = newViewModel()
        val state = vm.uiState.value
        assertEquals(formatLocalDate(state.shootDate), state.title)
        assertTrue(state.canSave)
    }

    @Test
    fun shootDateChange_updatesTitle_whenTitleNotManuallyEdited() {
        val vm = newViewModel()
        vm.onShootDateChange(LocalDate(2026, 3, 5))
        assertEquals("2026-03-05", vm.uiState.value.title)
        assertTrue(vm.uiState.value.canSave)
    }

    @Test
    fun shootDateChange_keepsTitle_whenTitleManuallyEdited() {
        val vm = newViewModel()
        vm.onTitleChange("我的记录")
        vm.onShootDateChange(LocalDate(2026, 3, 5))
        assertEquals("我的记录", vm.uiState.value.title)
    }

    @Test
    fun titleCleared_disablesSave() {
        val vm = newViewModel()
        vm.onTitleChange("")
        assertFalse(vm.uiState.value.canSave)
    }

    @Test
    fun existingRecord_loadsRecordData_withoutNewPhotoPath() = runTest(testDispatcher) {
        val rootDir = File("build/tmp/record_edit_test")
        val existingRecord = DateRecord(
            id = 5L,
            title = "旧标题",
            note = "旧备注",
            shootDate = LocalDate(2026, 1, 1),
            linkedDate = null,
            photoPath = "Pictures/date_recorder/old.jpg",
            createdAt = Instant.fromEpochMilliseconds(100000L)
        )
        val dao = FakeDateRecordDao(existingRecord)
        val repository = DateRecorderRepository(dao, rootDir)

        val vm = RecordEditViewModel(repository, photoPath = null, recordId = 5L)
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("旧标题", state.title)
        assertEquals("旧备注", state.note)
        assertTrue(state.isExistingRecord)
        assertEquals(File(rootDir, "Pictures/date_recorder/old.jpg").absolutePath, state.photoAbsolutePath)
    }

    @Test
    fun existingRecord_usesNewPhotoPath_whenPhotoPathProvided() = runTest(testDispatcher) {
        val rootDir = File("build/tmp/record_edit_test")
        val existingRecord = DateRecord(
            id = 5L,
            title = "旧标题",
            note = "旧备注",
            shootDate = LocalDate(2026, 1, 1),
            linkedDate = null,
            photoPath = "Pictures/date_recorder/old.jpg",
            createdAt = Instant.fromEpochMilliseconds(100000L)
        )
        val dao = FakeDateRecordDao(existingRecord)
        val repository = DateRecorderRepository(dao, rootDir)

        val newPhotoFile = File(rootDir, "Pictures/date_recorder/edited.jpg")
        val vm = RecordEditViewModel(repository, photoPath = newPhotoFile.absolutePath, recordId = 5L)
        testScheduler.advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals("旧标题", state.title)
        assertTrue(state.isExistingRecord)
        assertEquals(newPhotoFile.absolutePath, state.photoAbsolutePath)

        vm.save()
        testScheduler.advanceUntilIdle()
        assertEquals(5L, dao.updatedRecord?.id)
        assertEquals("Pictures/date_recorder/edited.jpg", dao.updatedRecord?.photoPath)
        assertEquals(Instant.fromEpochMilliseconds(100000L), dao.updatedRecord?.createdAt)
    }
}

private class FakeDateRecordDao(
    private val record: DateRecord? = null
) : DateRecordDao {
    var updatedRecord: DateRecord? = null
    override fun getByIdFlow(id: Long): Flow<DateRecord?> = flowOf(record)
    override fun getAllFlow(): Flow<List<DateRecord>> = flowOf(record?.let { listOf(it) } ?: emptyList())
    override suspend fun insert(record: DateRecord): Long = 0
    override suspend fun update(record: DateRecord) {
        this.updatedRecord = record
    }
    override suspend fun deleteByIds(ids: List<Long>): Int = 0
}

