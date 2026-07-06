package plus.rua.project

import android.content.Context
import android.content.SharedPreferences
import kotlinx.datetime.LocalDate

/**
 * 个人轮班设置持久化。照抄 [DateCheckerStorage] 模式。
 *
 * 编码格式(无 JSON 依赖):
 * - 锚点:ISO 日期串 "2026-07-08"
 * - 周期:逗号分隔的 1/0 串 "1,1,0,0"(1=WORK,0=OFF)
 * - overrides/breaks:逗号分隔的 "日期:值" 对
 *   - overrides 值:1=WORK,0=OFF
 *   - breaks 值:cycleOffset(整数)
 */
class ShiftPatternStorage(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_ANCHOR = "shift_anchor"
        private const val KEY_CYCLE = "shift_cycle"
        private const val KEY_OVERRIDES = "shift_overrides"
        private const val KEY_BREAKS = "shift_breaks"
        private const val SEP = ","

        fun fromContext(context: Context): ShiftPatternStorage =
            ShiftPatternStorage(
                context.getSharedPreferences("shift_pattern", Context.MODE_PRIVATE)
            )
    }

    fun save(pattern: ShiftPattern) {
        val cycleStr = pattern.cycle.joinToString(SEP) { if (it == ShiftKind.WORK) "1" else "0" }
        val overridesStr = pattern.overrides.entries
            .joinToString(SEP) { "${it.key}:${if (it.value == ShiftKind.WORK) 1 else 0}" }
        val breaksStr = pattern.phaseBreaks
            .joinToString(SEP) { "${it.date}:${it.cycleOffset}" }
        prefs.edit()
            .putString(KEY_ANCHOR, pattern.anchorDate.toString())
            .putString(KEY_CYCLE, cycleStr)
            .putString(KEY_OVERRIDES, overridesStr)
            .putString(KEY_BREAKS, breaksStr)
            .apply()
    }

    fun load(): ShiftPattern? {
        val anchorStr = prefs.getString(KEY_ANCHOR, null) ?: return null
        val cycleStr = prefs.getString(KEY_CYCLE, null) ?: return null
        return try {
            val anchor = LocalDate.parse(anchorStr)
            val cycle = cycleStr.split(SEP).map { if (it.trim() == "1") ShiftKind.WORK else ShiftKind.OFF }
            val overrides = parseOverrides(prefs.getString(KEY_OVERRIDES, null))
            val breaks = parseBreaks(prefs.getString(KEY_BREAKS, null))
            ShiftPattern(anchor, cycle, overrides, breaks)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseOverrides(s: String?): Map<LocalDate, ShiftKind> {
        if (s.isNullOrBlank()) return emptyMap()
        return s.split(SEP).associate { pair ->
            val parts = pair.split(":")
            LocalDate.parse(parts[0]) to (if (parts[1].trim() == "1") ShiftKind.WORK else ShiftKind.OFF)
        }
    }

    private fun parseBreaks(s: String?): List<PhaseBreak> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split(SEP).map { pair ->
            val parts = pair.split(":")
            PhaseBreak(LocalDate.parse(parts[0]), parts[1].trim().toInt())
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
