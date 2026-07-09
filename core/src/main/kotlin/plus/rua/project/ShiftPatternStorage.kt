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
 * - overrides:逗号分隔的 "日期:值" 对,值 1=WORK,0=OFF
 * - rephaseFlips:逗号分隔的 "翻转日:值:重排起点" 三元组,值 1=WORK,0=OFF
 */
class ShiftPatternStorage(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_ANCHOR = "shift_anchor"
        private const val KEY_CYCLE = "shift_cycle"
        private const val KEY_OVERRIDES = "shift_overrides"
        private const val KEY_REPHASE = "shift_rephase"
        private const val KEY_NAME = "shift_name"
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
        val rephaseStr = pattern.rephaseFlips
            .joinToString(SEP) {
                "${it.date}:${if (it.flippedTo == ShiftKind.WORK) 1 else 0}:${it.rephaseFrom}"
            }
        prefs.edit()
            .putString(KEY_ANCHOR, pattern.anchorDate.toString())
            .putString(KEY_CYCLE, cycleStr)
            .putString(KEY_OVERRIDES, overridesStr)
            .putString(KEY_REPHASE, rephaseStr)
            .putString(KEY_NAME, pattern.name)
            .apply()
    }

    fun load(): ShiftPattern? {
        val anchorStr = prefs.getString(KEY_ANCHOR, null) ?: return null
        val cycleStr = prefs.getString(KEY_CYCLE, null) ?: return null
        return try {
            val anchor = LocalDate.parse(anchorStr)
            val cycle = if (cycleStr.isBlank()) {
                emptyList()
            } else {
                cycleStr.split(SEP).map { if (it.trim() == "1") ShiftKind.WORK else ShiftKind.OFF }
            }
            val overrides = parseOverrides(prefs.getString(KEY_OVERRIDES, null))
            val rephaseFlips = parseRephase(prefs.getString(KEY_REPHASE, null))
            ShiftPattern(anchor, cycle, overrides, rephaseFlips, name = prefs.getString(KEY_NAME, null) ?: "默认")
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

    private fun parseRephase(s: String?): List<RephaseFlip> {
        if (s.isNullOrBlank()) return emptyList()
        return s.split(SEP).map { triple ->
            // 格式:翻转日:值:重排起点(ISO 日期不含冒号,split 安全)
            val colonIdx = triple.indexOf(':')
            val lastColon = triple.lastIndexOf(':')
            val date = LocalDate.parse(triple.substring(0, colonIdx))
            val flippedTo = if (triple.substring(colonIdx + 1, lastColon).trim() == "1") ShiftKind.WORK else ShiftKind.OFF
            val rephaseFrom = LocalDate.parse(triple.substring(lastColon + 1))
            RephaseFlip(date, flippedTo, rephaseFrom)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
