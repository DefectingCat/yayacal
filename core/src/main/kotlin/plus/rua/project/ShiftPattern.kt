package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * 个人轮班类型。仅区分上班与休息;后续可扩展早/中/晚班、休假等。
 */
enum class ShiftKind { WORK, OFF }

/**
 * 相位断点:从 [date] 起重排周期相位,[cycleOffset] 指定当天对应 cycle 的第几位。
 *
 * @param date 断点生效日(含当天)
 * @param cycleOffset 该天对应的 cycle 索引(0 = cycle[0])
 */
data class PhaseBreak(
    val date: LocalDate,
    val cycleOffset: Int
)

/**
 * 个人轮班周期。
 *
 * 与法定节假日完全独立。某天的班/休由以下顺序决定:
 * 1. 若 [overrides] 命中该天,取 override 值(单日翻转);
 * 2. 否则取该天"活跃锚点"(最近的 phaseBreak 或基础 anchorDate)起算的 cycle 索引。
 *
 * @param anchorDate 基础周期基准日,对应 cycle[0]
 * @param cycle 一个周期内的班次序列,例如 [WORK, WORK, OFF, OFF] 表示 "2 班 2 休"
 * @param overrides 单日翻转映射(调班),key 为日期
 * @param phaseBreaks 相位断点列表,从某天起重排周期相位
 * @param name 方案名
 */
data class ShiftPattern(
    val anchorDate: LocalDate,
    val cycle: List<ShiftKind>,
    val overrides: Map<LocalDate, ShiftKind> = emptyMap(),
    val phaseBreaks: List<PhaseBreak> = emptyList(),
    val name: String = "默认"
) {
    fun kindAt(date: LocalDate): ShiftKind? {
        if (cycle.isEmpty()) return null
        // 1. 单日翻转优先
        overrides[date]?.let { return it }
        // 2. 找活跃锚点:phaseBreaks 中 date <= 当天 的最大那个;无则用基础锚点
        val (anchor, offset) = activeAnchor(date)
        val diff = anchor.daysUntil(date)
        val size = cycle.size
        val idx = (((diff + offset) % size) + size) % size
        return cycle[idx]
    }

    private fun activeAnchor(date: LocalDate): Pair<LocalDate, Int> {
        val applicable = phaseBreaks.filter { it.date <= date }.maxByOrNull { it.date }
        return if (applicable != null) applicable.date to applicable.cycleOffset
        else anchorDate to 0
    }
}
