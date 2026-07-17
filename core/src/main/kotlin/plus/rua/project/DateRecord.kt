package plus.rua.project

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * 日期记录器的一条记录。
 *
 * 每条记录对应一张照片及相关的文字信息，照片文件路径相对于应用 filesDir。
 *
 * @param id 自增主键，新建时传 0
 * @param title 标题
 * @param note 备注正文
 * @param shootDate 拍摄日期
 * @param linkedDate 关联到日历的某一天，可为空表示不关联
 * @param photoPath 照片文件相对路径（相对 filesDir）
 * @param createdAt 记录创建时间
 */
@Entity(tableName = "date_records")
data class DateRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val shootDate: LocalDate,
    val linkedDate: LocalDate?,
    val photoPath: String,
    val createdAt: Instant
)
