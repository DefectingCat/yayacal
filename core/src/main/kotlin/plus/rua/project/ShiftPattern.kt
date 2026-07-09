package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * 个人轮班类型。仅区分上班与休息;后续可扩展早/中/晚班、休假等。
 */
enum class ShiftKind { WORK, OFF }

/**
 * "翻转并重排"的原子记录:翻转 [date] 当天为 [flippedTo],
 * 并从 [rephaseFrom] 起重排周期相位(后续按 cycle 重新顺延)。
 *
 * date 与 rephaseFrom 通常相邻(rephaseFrom = date 的次日),
 * 作为单一操作的两面绑定存储,撤销时整体删除,不会留下孤立数据。
 *
 * @param date 被翻转的天
 * @param flippedTo 翻转后的班次
 * @param rephaseFrom 重排起点(含当天),该天起按 cycle[0] 重新循环
 */
data class RephaseFlip(
    val date: LocalDate,
    val flippedTo: ShiftKind,
    val rephaseFrom: LocalDate
)

/**
 * 个人轮班周期。
 *
 * 与法定节假日完全独立。某天的班/休由以下顺序决定:
 * 1. 若 [overrides] 命中该天,取 override 值(单日翻转,仅当天);
 * 2. 否则若 [rephaseFlips] 中有该天作为翻转日的记录,取 [RephaseFlip.flippedTo];
 * 3. 否则取该天"活跃锚点"(最近的 rephaseFlips.rephaseFrom 或基础 anchorDate)
 *    起算的 cycle 索引。
 *
 * @param anchorDate 基础周期基准日,对应 cycle[0]
 * @param cycle 一个周期内的班次序列,例如 [WORK, WORK, OFF, OFF] 表示 "2 班 2 休"
 * @param overrides 单日翻转映射(仅当天,不影响后续),key 为日期
 * @param rephaseFlips 翻转并重排记录列表;每条翻转某天并从次日起重排周期
 * @param name 方案名
 */
data class ShiftPattern(
    val anchorDate: LocalDate,
    val cycle: List<ShiftKind>,
    val overrides: Map<LocalDate, ShiftKind> = emptyMap(),
    val rephaseFlips: List<RephaseFlip> = emptyList(),
    val name: String = "默认"
) {
    /**
     * 返回 [date] 当天的班次。优先级:overrides → rephaseFlip 当天 → 活跃锚点的 cycle 索引。
     * cycle 为空时返回 null。
     */
    fun kindAt(date: LocalDate): ShiftKind? {
        if (cycle.isEmpty()) return null
        // 1. 单日翻转优先(仅当天)
        overrides[date]?.let { return it }
        // 2. rephaseFlips 中被翻转的当天
        rephaseFlips.find { it.date == date }?.let { return it.flippedTo }
        // 3. 找活跃锚点:rephaseFlips 中 rephaseFrom <= 当天 的最大那个;无则用基础锚点
        val anchor = activeAnchor(date)
        val diff = anchor.daysUntil(date)
        val size = cycle.size
        val idx = ((diff % size) + size) % size
        return cycle[idx]
    }

    private fun activeAnchor(date: LocalDate): LocalDate {
        return rephaseFlips
            .filter { it.rephaseFrom <= date }
            .maxByOrNull { it.rephaseFrom }
            ?.rephaseFrom
            ?: anchorDate
    }
}
