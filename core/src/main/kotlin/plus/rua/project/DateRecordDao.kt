package plus.rua.project

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 日期记录器的数据库访问对象。
 *
 * 所有读操作返回 [Flow]，UI 层订阅后自动响应数据库变化。
 */
@Dao
interface DateRecordDao {

    /**
     * 按 ID 查询单条记录的 Flow，用于详情页订阅。
     *
     * @param id 记录主键
     * @return 该 ID 的记录 Flow，不存在时 emit null
     */
    @Query("SELECT * FROM date_records WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<DateRecord?>

    /**
     * 查询全部记录，默认按拍摄日期降序。
     *
     * 最终排序在 Repository / UI 层根据用户选择重新应用，此处仅保证一个稳定默认序。
     */
    @Query("SELECT * FROM date_records ORDER BY shootDate DESC, id DESC")
    fun getAllFlow(): Flow<List<DateRecord>>

    /**
     * 插入一条新记录。
     *
     * @return 新记录的自增 ID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DateRecord): Long

    /**
     * 更新一条已有记录。
     */
    @Update
    suspend fun update(record: DateRecord)

    /**
     * 按 ID 列表批量删除，支持多选删除场景。
     *
     * @param ids 待删除记录的主键列表
     * @return 实际删除的行数
     */
    @Query("DELETE FROM date_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>): Int
}
