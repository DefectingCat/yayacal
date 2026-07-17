package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * 日期记录器排序逻辑单元测试。
 *
 * 覆盖 [DateRecorderViewModel.sortDateRecords] 的所有字段与升降序组合，
 * 以及无关联日期记录的末尾排列。
 */
class DateRecorderSortTest {

    private val records = listOf(
        record(id = 1, title = "A", shoot = LocalDate(2026, 1, 1), linked = LocalDate(2026, 1, 1), created = Instant.fromEpochSeconds(1000)),
        record(id = 2, title = "B", shoot = LocalDate(2026, 3, 1), linked = null, created = Instant.fromEpochSeconds(2000)),
        record(id = 3, title = "C", shoot = LocalDate(2026, 2, 1), linked = LocalDate(2026, 2, 1), created = Instant.fromEpochSeconds(3000))
    )

    @Test
    fun sortByShootDate_descending_newestFirst() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            records,
            RecordSortOrder(RecordSortField.SHOOT_DATE, ascending = false)
        )
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun sortByShootDate_ascending_oldestFirst() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            records,
            RecordSortOrder(RecordSortField.SHOOT_DATE, ascending = true)
        )
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun sortByLinkedDate_descending_nullsFirst() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            records,
            RecordSortOrder(RecordSortField.LINKED_DATE, ascending = false)
        )
        // 降序时 reversed() 把 nullsLast 翻转为 nullsFirst：
        // null(id2) → 2026-02-01(id3) → 2026-01-01(id1)
        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    @Test
    fun sortByLinkedDate_ascending_nullsLast() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            records,
            RecordSortOrder(RecordSortField.LINKED_DATE, ascending = true)
        )
        // 升序：2026-01-01(id1) → 2026-02-01(id3) → null(id2)
        assertEquals(listOf(1L, 3L, 2L), sorted.map { it.id })
    }

    @Test
    fun sortByCreatedAt_descending() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            records,
            RecordSortOrder(RecordSortField.CREATED_AT, ascending = false)
        )
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun sortByCreatedAt_ascending() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            records,
            RecordSortOrder(RecordSortField.CREATED_AT, ascending = true)
        )
        assertEquals(listOf(1L, 2L, 3L), sorted.map { it.id })
    }

    @Test
    fun sort_emptyList_returnsEmpty() {
        val sorted = DateRecorderViewModel.sortDateRecords(
            emptyList(),
            RecordSortOrder.DEFAULT
        )
        assertEquals(emptyList(), sorted)
    }

    @Test
    fun sort_tieBreakerById_ascending() {
        // 同一拍摄日期、同一创建时间，仅 id 不同 → 按 id 升序
        val tied = listOf(
            record(id = 5, shoot = LocalDate(2026, 1, 1), created = Instant.fromEpochSeconds(0)),
            record(id = 2, shoot = LocalDate(2026, 1, 1), created = Instant.fromEpochSeconds(0)),
            record(id = 8, shoot = LocalDate(2026, 1, 1), created = Instant.fromEpochSeconds(0))
        )
        val sorted = DateRecorderViewModel.sortDateRecords(
            tied,
            RecordSortOrder(RecordSortField.SHOOT_DATE, ascending = true)
        )
        // 主键 id 升序兜底
        assertEquals(listOf(2L, 5L, 8L), sorted.map { it.id })
    }

    private fun record(
        id: Long,
        title: String = "t",
        shoot: LocalDate = LocalDate(2026, 1, 1),
        linked: LocalDate? = null,
        created: Instant = Instant.fromEpochSeconds(0)
    ) = DateRecord(
        id = id,
        title = title,
        note = "",
        shootDate = shoot,
        linkedDate = linked,
        photoPath = "fake/path.jpg",
        createdAt = created
    )
}
