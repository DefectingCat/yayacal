package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class PeriodSyncStorageTest {
    private val prefs = InMemoryTestPrefs()
    private val storage = PeriodSyncStorage(prefs)
    private val day = LocalDate(2026, 10, 9)

    @Test
    fun saveAndLoad_snapshotAndEveryOpType_roundTrips() {
        val snapshot =
            PeriodDocument(
                revision = 12,
                settings = PeriodSettings(30, 6, 13),
                ranges = listOf(PeriodRange(LocalDate(2026, 9, 12), LocalDate(2026, 9, 16)), PeriodRange(day, null)),
                notes = listOf(PeriodNote(day, PeriodMood.TIRED, "腰酸 \"热水袋\""), PeriodNote(LocalDate(2026, 10, 10), null, "备注")),
            )
        val ops =
            listOf(
                PeriodOp.StartPeriod(day),
                PeriodOp.EndPeriod(day),
                PeriodOp.SetPeriodDay(day, false, LocalDate(2026, 10, 11)),
                PeriodOp.UpdateRange(day, LocalDate(2026, 10, 8), null),
                PeriodOp.UpdateRange(day, LocalDate(2026, 10, 8), LocalDate(2026, 10, 12)),
                PeriodOp.DeleteRange(day),
                PeriodOp.SetNote(day, null, "只有备注"),
                PeriodOp.SetNote(day, PeriodMood.HAPPY, ""),
                PeriodOp.UpdateSettings(PeriodSettings(21, 4, 12)),
            )
        val state = PeriodLocalState(snapshot, ops.mapIndexed { index, op -> PendingPeriodOp(index + 3L, op) }, nextSeq = 12)

        storage.save("https://a.example", state)

        assertEquals(state, storage.load("https://a.example"))
    }

    @Test
    fun load_otherServerUrl_isIsolated() {
        storage.save("https://a.example", PeriodLocalState(PeriodDocument(revision = 3), listOf(PendingPeriodOp(1, PeriodOp.StartPeriod(day))), 2))

        assertEquals(PeriodLocalState(), storage.load("https://b.example"))
    }

    @Test
    fun load_corruptData_fallsBackToEmptyState() {
        prefs.data["snapshot:https://a.example"] = "{not json"
        prefs.data["pending:https://a.example"] = "[{\"seq\":1}]"

        assertEquals(PeriodLocalState(), storage.load("https://a.example"))
    }

    @Test
    fun load_nextSeqBehindPending_staysAfterHighestSeq() {
        storage.save("https://a.example", PeriodLocalState(null, listOf(PendingPeriodOp(9, PeriodOp.StartPeriod(day))), nextSeq = 1))

        assertEquals(10, storage.load("https://a.example").nextSeq)
    }
}
