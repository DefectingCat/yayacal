package plus.rua.project

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * [DateRecorderViewModel] 的多选模式与选择状态单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DateRecorderViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): DateRecorderViewModel {
        val repository = DateRecorderRepository(FakeDateRecorderDao(), File("build/tmp/date_recorder_vm_test"))
        return DateRecorderViewModel(repository)
    }

    @Test
    fun startSelectionModeWith_enablesSelectionMode_andSelectsSpecifiedId() = runTest(testDispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        assertFalse(vm.uiState.value.selectionMode)

        vm.startSelectionModeWith(42L)

        assertTrue(vm.uiState.value.selectionMode)
        assertEquals(setOf(42L), vm.uiState.value.selectedIds)
    }

    @Test
    fun toggleSelectionMode_togglesState_andClearsSelection() = runTest(testDispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.startSelectionModeWith(100L)
        assertTrue(vm.uiState.value.selectionMode)

        vm.toggleSelectionMode()
        assertFalse(vm.uiState.value.selectionMode)
        assertTrue(vm.uiState.value.selectedIds.isEmpty())
    }

    @Test
    fun toggleSelection_addsAndRemovesId() = runTest(testDispatcher) {
        val vm = newViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.startSelectionModeWith(1L)

        vm.toggleSelection(2L)
        assertEquals(setOf(1L, 2L), vm.uiState.value.selectedIds)

        vm.toggleSelection(1L)
        assertEquals(setOf(2L), vm.uiState.value.selectedIds)
    }
}

private class FakeDateRecorderDao : DateRecordDao {
    override fun getByIdFlow(id: Long): Flow<DateRecord?> = flowOf(null)
    override fun getAllFlow(): Flow<List<DateRecord>> = flowOf(emptyList())
    override suspend fun insert(record: DateRecord): Long = 0
    override suspend fun update(record: DateRecord) = Unit
    override suspend fun deleteByIds(ids: List<Long>): Int = 0
}
