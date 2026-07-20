package plus.rua.project

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 新建记录标题预填与拍摄日期联动的单元测试。
 *
 * 新建模式的被测路径不会触发 DAO 调用，[FakeDateRecordDao] 返回空数据即可。
 */
class RecordEditViewModelTest {

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
}

private class FakeDateRecordDao : DateRecordDao {
    override fun getByIdFlow(id: Long): Flow<DateRecord?> = flowOf(null)
    override fun getAllFlow(): Flow<List<DateRecord>> = flowOf(emptyList())
    override suspend fun insert(record: DateRecord): Long = 0
    override suspend fun update(record: DateRecord) = Unit
    override suspend fun deleteByIds(ids: List<Long>): Int = 0
}
