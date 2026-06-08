package plus.rua.project

import android.content.Context
import android.content.SharedPreferences
import kotlinx.datetime.LocalDate

class DateCheckerStorage(private val prefs: SharedPreferences) {

    companion object {
        private const val KEY_PRODUCTION_DATE = "production_date"
        private const val KEY_ROWS = "rows"
        private const val ROWS_SEPARATOR = ","

        fun fromContext(context: Context): DateCheckerStorage {
            val prefs = context.getSharedPreferences("date_checker", Context.MODE_PRIVATE)
            return DateCheckerStorage(prefs)
        }
    }

    fun save(productionDate: LocalDate, rows: List<Int?>) {
        val nonNullRows = rows.filterNotNull()
        prefs.edit()
            .putString(KEY_PRODUCTION_DATE, productionDate.toString())
            .putString(KEY_ROWS, nonNullRows.joinToString(ROWS_SEPARATOR))
            .apply()
    }

    fun load(): Pair<LocalDate, List<Int>>? {
        val dateStr = prefs.getString(KEY_PRODUCTION_DATE, null) ?: return null
        val rowsStr = prefs.getString(KEY_ROWS, null) ?: return null
        val date = LocalDate.parse(dateStr)
        val rows = if (rowsStr.isBlank()) {
            emptyList()
        } else {
            rowsStr.split(ROWS_SEPARATOR).map { it.toInt() }
        }
        return date to rows
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
