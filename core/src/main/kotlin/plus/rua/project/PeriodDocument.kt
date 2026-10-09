package plus.rua.project

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** 每日心情；[key] 与后端约定一致，不随文案变化。 */
enum class PeriodMood(
    val key: String,
    val label: String,
    val emoji: String,
) {
    HAPPY("happy", "开心", "😊"),
    CALM("calm", "平静", "😌"),
    TIRED("tired", "疲惫", "😪"),
    LOW("low", "低落", "😔"),
    IRRITABLE("irritable", "烦躁", "😠"),
    ;

    companion object {
        fun fromKey(key: String?): PeriodMood? = entries.firstOrNull { it.key == key }
    }
}

/** 一次经期；[end] 为 null 表示进行中。区间互不重叠，[start] 即身份。 */
data class PeriodRange(
    val start: LocalDate,
    val end: LocalDate?,
) {
    val isOngoing: Boolean get() = end == null

    /** 进行中的经期按 [today] 展开；开始日在未来时只覆盖开始日。 */
    fun endOn(today: LocalDate): LocalDate = end ?: maxOf(start, today)

    fun contains(
        date: LocalDate,
        today: LocalDate,
    ): Boolean = date >= start && date <= endOn(today)
}

/** 某天的心情与备注，与是否在经期无关。 */
data class PeriodNote(
    val date: LocalDate,
    val mood: PeriodMood?,
    val text: String,
)

/** 预测用默认值；记录不足时生效，随文档同步。 */
data class PeriodSettings(
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lutealLength: Int = 14,
) {
    companion object {
        val CYCLE_RANGE = 15..60
        val PERIOD_RANGE = 2..10
        val LUTEAL_RANGE = 10..16
    }
}

/**
 * 与后端同步的整份经期文档。
 *
 * 不变量见 [violation]；所有修改经 [normalized] 后再校验，保证提交给后端的文档总能通过校验。
 */
data class PeriodDocument(
    val revision: Long = 0,
    val settings: PeriodSettings = PeriodSettings(),
    val ranges: List<PeriodRange> = emptyList(),
    val notes: List<PeriodNote> = emptyList(),
) {
    val ongoing: PeriodRange? get() = ranges.lastOrNull()?.takeIf { it.isOngoing }

    fun noteAt(date: LocalDate): PeriodNote? = notes.firstOrNull { it.date == date }

    fun rangeAt(
        date: LocalDate,
        today: LocalDate,
    ): PeriodRange? = ranges.firstOrNull { it.contains(date, today) }

    /** 忽略修订号比较内容，用于判断重放后是否真有改动。 */
    fun sameContentAs(other: PeriodDocument): Boolean = copy(revision = 0) == other.copy(revision = 0)

    /**
     * 排序并合并重叠或相邻的区间，去除备注首尾空白、删除空备注，同一天保留最后一条。
     */
    fun normalized(): PeriodDocument {
        val merged = mutableListOf<PeriodRange>()
        for (range in ranges.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            val lastEnd = last?.end
            if (last == null || (lastEnd != null && range.start > lastEnd.plus(DatePeriod(days = 1)))) {
                merged += range
                continue
            }
            // 进行中的区间吸收其后所有区间；否则结束日取两者较晚者
            val end = if (lastEnd == null || range.end == null) null else maxOf(lastEnd, range.end)
            merged[merged.lastIndex] = last.copy(end = end)
        }
        val cleaned =
            notes
                .map { it.copy(text = it.text.trim()) }
                .filter { it.mood != null || it.text.isNotEmpty() }
                .associateBy { it.date }
                .values
                .sortedBy { it.date }
        return copy(ranges = merged, notes = cleaned)
    }

    /** 与后端 `validate` 一致的规则；返回首个违反项的中文原因，合法时为 null。 */
    fun violation(): String? {
        if (settings.cycleLength !in PeriodSettings.CYCLE_RANGE ||
            settings.periodLength !in PeriodSettings.PERIOD_RANGE ||
            settings.lutealLength !in PeriodSettings.LUTEAL_RANGE
        ) {
            return "周期需为 15–60 天、经期 2–10 天、黄体期 10–16 天"
        }
        ranges.forEachIndexed { index, range ->
            if (range.start !in DATE_BOUNDS) return DATE_BOUNDS_MESSAGE
            val end = range.end
            if (end == null) {
                if (index != ranges.lastIndex) return "只能有一段进行中的经期，且必须是最近一段"
                return@forEachIndexed
            }
            if (end !in DATE_BOUNDS) return DATE_BOUNDS_MESSAGE
            if (end < range.start) return "经期结束日期不能早于开始日期"
            if (range.start.daysUntil(end) >= MAX_CLOSED_DAYS) return "单次经期不能超过 31 天"
            val next = ranges.getOrNull(index + 1)
            if (next != null && next.start <= end.plus(DatePeriod(days = 1))) return "经期区间不能重叠或相邻"
        }
        notes.forEachIndexed { index, note ->
            if (note.date !in DATE_BOUNDS) return DATE_BOUNDS_MESSAGE
            if (index > 0 && notes[index - 1].date >= note.date) return "同一天只能有一条心情与备注"
            if (note.text.noteLength() > MAX_NOTE_CHARS) return "备注最多 500 字"
            if (note.mood == null && note.text.isBlank()) return "心情和备注不能都为空"
        }
        return null
    }

    companion object {
        const val MAX_NOTE_CHARS = 500
        const val MAX_CLOSED_DAYS = 31
        val DATE_BOUNDS = LocalDate(2000, 1, 1)..LocalDate(2100, 12, 31)
        private const val DATE_BOUNDS_MESSAGE = "日期需在 2000 年至 2100 年之间"

        /** 已结束经期允许的最晚结束日，与 [MAX_CLOSED_DAYS] 对应。 */
        fun latestEndFor(start: LocalDate): LocalDate = start.plus(DatePeriod(days = MAX_CLOSED_DAYS - 1))

        /** [date] 前一天，供区间拆分使用。 */
        internal fun dayBefore(date: LocalDate): LocalDate = date.minus(DatePeriod(days = 1))

        /** [date] 后一天。 */
        internal fun dayAfter(date: LocalDate): LocalDate = date.plus(DatePeriod(days = 1))
    }
}

/** 按 Unicode 码点计数，与后端 `chars().count()` 一致，emoji 计为 1 个字。 */
fun String.noteLength(): Int = codePointCount(0, length)
