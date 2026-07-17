package plus.rua.project

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 日期记录器的 Room 数据库。
 *
 * 应用内单例，通过 [fromContext] 获取实例。Schema 导出到 `core/schemas/` 以追踪版本演进。
 */
@Database(
    entities = [DateRecord::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateRecordConverters::class)
abstract class DateRecordDatabase : RoomDatabase() {

    abstract fun dateRecordDao(): DateRecordDao

    companion object {
        @Volatile
        private var INSTANCE: DateRecordDatabase? = null

        /**
         * 获取数据库单例。
         *
         * @param context 任意 Context，内部取 applicationContext
         */
        fun fromContext(context: Context): DateRecordDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DateRecordDatabase::class.java,
                    "date_recorder.db"
                ).build().also { INSTANCE = it }
            }
    }
}
