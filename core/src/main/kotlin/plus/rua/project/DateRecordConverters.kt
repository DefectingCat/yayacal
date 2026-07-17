package plus.rua.project

import androidx.room.TypeConverter
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * Room 的 kotlinx-datetime 类型转换器。
 *
 * Room 默认不识别 [LocalDate] 与 [Instant]，这里统一以 ISO 字符串持久化：
 * - [LocalDate] → "2026-07-17"
 * - [Instant] → "2026-07-17T08:30:00Z"
 *
 * ISO 格式可读、可排序、可跨版本演进，无需关心 epoch 精度。
 */
class DateRecordConverters {

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? =
        value?.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromInstant(instant: Instant?): String? = instant?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { Instant.parse(it) }
}
