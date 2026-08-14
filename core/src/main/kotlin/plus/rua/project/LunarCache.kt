package plus.rua.project

import com.tyme.solar.SolarDay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

/**
 * 农历/节气/节假日信息缓存。
 *
 * 使用 LinkedHashMap（accessOrder=true）实现 LRU 语义。
 * 通过 [Mutex] 保护并发访问，协程友好，不阻塞线程。
 *
 * @param maxSize 缓存最大容量，默认 800
 */
class LunarCache(
    private val maxSize: Int = MAX_SIZE,
) {
    private val mutex = Mutex()

    private val cache = LinkedHashMap<LocalDate, DayCellInfo>(256, 0.75f, true)

    /**
     * 获取指定日期的信息，缓存 miss 时计算。
     */
    suspend fun getOrCompute(date: LocalDate): DayCellInfo = mutex.withLock {
        cache[date]?.let { return@withLock it }
        val computed = compute(date)
        cache[date] = computed
        trimIfNeeded()
        computed
    }

    /**
     * 批量获取指定日期的信息，一次性加锁并返回 Map。
     *
     * @param dates 日期列表
     * @return 日期 → DayCellInfo 的映射
     */
    suspend fun getOrComputeBatch(dates: List<LocalDate>): Map<LocalDate, DayCellInfo> = mutex.withLock {
        val result = HashMap<LocalDate, DayCellInfo>(dates.size)
        var modified = false
        for (date in dates) {
            val cached = cache[date]
            if (cached != null) {
                result[date] = cached
            } else {
                val computed = compute(date)
                cache[date] = computed
                result[date] = computed
                modified = true
            }
        }
        if (modified) {
            trimIfNeeded()
        }
        result
    }

    /**
     * 批量预计算并填充缓存。
     *
     * @param dates 日期列表
     */
    suspend fun precompute(dates: List<LocalDate>) = mutex.withLock {
        dates.forEach { date ->
            if (!cache.containsKey(date)) {
                cache[date] = compute(date)
            }
        }
        trimIfNeeded()
    }

    /**
     * 获取完整农历日期字符串，如"农历四月初三"。
     *
     * 独立计算农历月名与日名，不复用缓存中的 annotationText —— 后者在
     * 节日/节气当天保存的是节日名（如"春节"），会导致输出错误。
     * 与 [plus.rua.project.ui.formatLunarDate] 保持一致。
     */
    suspend fun formatLunarDate(date: LocalDate): String {
        getOrCompute(date) // 预热缓存
        val solarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
        val lunarDay = solarDay.getLunarDay()
        val lunarMonth = lunarDay.getLunarMonth()
        return "农历${lunarMonth.getName()}${lunarDay.getName()}"
    }

    private fun trimIfNeeded() {
        while (cache.size > maxSize) {
            val iterator = cache.keys.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            } else {
                break
            }
        }
    }

    private fun compute(date: LocalDate): DayCellInfo {
        val solarDay = SolarDay.fromYmd(date.year, date.month.number, date.day)
        val holidayBadge = solarDay.getLegalHoliday()?.let { if (it.isWork()) "班" else "休" }
        val lunarDay = solarDay.getLunarDay()
        val lunarMonth = lunarDay.getLunarMonth()
        val lunarMonthName = lunarMonth.getName()
        // 阳历生日：每年 9 月 4 日（单一数据源见 [AnniversaryDates]）
        val isSolarBirthday =
            date.month.number == AnniversaryDates.DUCK_BIRTHDAY_MONTH &&
                date.day == AnniversaryDates.DUCK_BIRTHDAY_DAY
        // 农历生日：每年正月廿一；tyme4kt 中正月 indexInYear = 0，
        // 农历日直接取 day 属性
        val isLunarBirthday =
            lunarMonth.getIndexInYear() == AnniversaryDates.DOG_LUNAR_MONTH - 1 &&
                lunarDay.day == AnniversaryDates.DOG_LUNAR_DAY
        val isBirthday = isSolarBirthday || isLunarBirthday
        // 玫瑰日：每年 10 月 16 日
        val isRoseDay = date.month.number == 10 && date.day == 16

        // 农历传统节日（仅当天）
        val lunarFestival = lunarDay.getFestival()
        if (lunarFestival != null) {
            return DayCellInfo(lunarFestival.getName(), true, holidayBadge, lunarMonthName, isBirthday, isRoseDay)
        }

        // 节气（当天才显示）
        val termDay = solarDay.getTermDay()
        if (termDay.getDayIndex() == 0) {
            return DayCellInfo(termDay.getSolarTerm().getName(), true, holidayBadge, lunarMonthName, isBirthday, isRoseDay)
        }

        // 公历节日（仅当天）
        val solarFestival = solarDay.getFestival()
        if (solarFestival != null) {
            return DayCellInfo(solarFestival.getName(), true, holidayBadge, lunarMonthName, isBirthday, isRoseDay)
        }

        // 默认：农历日期
        val name = lunarDay.getName()
        val text =
            if (name == "初一") {
                lunarMonthName
            } else {
                name
            }
        return DayCellInfo(text, false, holidayBadge, lunarMonthName, isBirthday, isRoseDay)
    }

    companion object {
        private const val MAX_SIZE = 800
        val default = LunarCache()
    }
}

/**
 * 日期单元格显示信息。
 *
 * @param annotationText 底部标注文字（农历/节气/节日）
 * @param isAnnotationHighlight 是否为高亮标注（节日/节气）
 * @param holidayBadge 法定调休角标（"班"/"休"/null）
 * @param isBirthday 是否为生日
 * @param isRoseDay 是否为玫瑰日（每年 10 月 16 日）
 */
data class DayCellInfo(
    val annotationText: String,
    val isAnnotationHighlight: Boolean,
    val holidayBadge: String?,
    val lunarMonthName: String? = null,
    val isBirthday: Boolean = false,
    val isRoseDay: Boolean = false,
)
