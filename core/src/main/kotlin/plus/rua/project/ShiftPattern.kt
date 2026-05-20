package plus.rua.project

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * 个人轮班类型。仅区分上班与休息;后续可扩展早/中/晚班、休假等。
 */
enum class ShiftKind { WORK, OFF }

/**
 * 个人轮班周期。
 *
 * 与法定节假日完全独立:周期内某天是 WORK 还是 OFF,只看
 * `(date - anchorDate) mod cycle.size` 在 cycle 中的取值,不受任何节假日/调休影响。
 *
 * @param anchorDate 周期基准日,对应 cycle[0]
 * @param cycle 一个周期内的班次序列,例如 [WORK, WORK, OFF, OFF] 表示 "2 班 2 休"
 * @param name 方案名,用于后续多套方案场景
 */
data class ShiftPattern(
    val anchorDate: LocalDate,
    val cycle: List<ShiftKind>,
    val name: String = "默认"
) {
    fun kindAt(date: LocalDate): ShiftKind? {
        if (cycle.isEmpty()) return null
        val diff = anchorDate.daysUntil(date)
        val size = cycle.size
        val idx = ((diff % size) + size) % size
        return cycle[idx]
    }
}
