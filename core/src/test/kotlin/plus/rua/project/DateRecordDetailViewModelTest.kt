package plus.rua.project

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * [DateRecordDetailViewModel] 的状态加载与删除功能单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DateRecordDetailViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val testRecord = DateRecord(
        id = 100L,
        title = "测试记录",
        note = "测试笔记",
        shootDate = LocalDate(2026, 7, 23),
        linkedDate = LocalDate(2026, 7, 23),
        photoPath = "test_photo.jpg",
        createdAt = Instant.fromEpochMilliseconds(1000000L)
    )

    @Test
    fun load_populatesRecordState_whenRecordExists() = runTest(testDispatcher) {
        val dao = DetailFakeDao(testRecord)
        val repository = DateRecorderRepository(dao, File("build/tmp/record_detail_test"))
        val vm = DateRecordDetailViewModel(repository, recordId = 100L)

        backgroundScope.launch { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertNotNull(state.record)
        assertEquals("测试记录", state.record?.title)
        assertNotNull(state.photoUri)
        assertTrue(state.photoUri!!.endsWith("test_photo.jpg"))
        assertTrue(vm.currentPhotoPath()?.endsWith("test_photo.jpg") == true)
    }

    @Test
    fun load_setsLoadingFalse_whenRecordNotFound() = runTest(testDispatcher) {
        val dao = DetailFakeDao(null)
        val repository = DateRecorderRepository(dao, File("build/tmp/record_detail_test"))
        val vm = DateRecordDetailViewModel(repository, recordId = 999L)

        backgroundScope.launch { vm.uiState.collect {} }

        val state = vm.uiState.value
        assertFalse(state.loading)
        assertNull(state.record)
        assertNull(vm.currentPhotoPath())
    }
    @Test
    fun delete_triggersRepositoryDelete_andSetsDeletedState() = runTest(testDispatcher) {
        val dao = DetailFakeDao(testRecord)
        val repository = DateRecorderRepository(dao, File("build/tmp/record_detail_test"))
        val vm = DateRecordDetailViewModel(repository, recordId = 100L)

        backgroundScope.launch { vm.uiState.collect {} }
        testScheduler.advanceUntilIdle()

        vm.delete()
        testScheduler.advanceUntilIdle()

        var retries = 0
        while (!vm.uiState.value.deleted && retries < 50) {
            Thread.sleep(20)
            retries++
        }

        assertTrue(vm.uiState.value.deleted)
        assertEquals(listOf(100L), dao.deletedIds)
    }
}

private class DetailFakeDao(initialRecord: DateRecord?) : DateRecordDao {
    val recordFlow = MutableStateFlow(initialRecord)
    val deletedIds = mutableListOf<Long>()

    override fun getByIdFlow(id: Long): Flow<DateRecord?> = recordFlow
    override fun getAllFlow(): Flow<List<DateRecord>> = flowOf(emptyList())
    override suspend fun insert(record: DateRecord): Long = record.id
    override suspend fun update(record: DateRecord) {
        recordFlow.value = record
    }
    override suspend fun deleteByIds(ids: List<Long>): Int {
        deletedIds.addAll(ids)
        recordFlow.value = null
        return ids.size
    }
}
