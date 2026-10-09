package plus.rua.project

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** 一条尚未推送到服务器的操作；[seq] 在同一服务地址内递增，用于只清除已推送的部分。 */
data class PendingPeriodOp(
    val seq: Long,
    val op: PeriodOp,
)

/** 某个服务地址下的本机状态：最近一次服务器快照与待同步操作。 */
data class PeriodLocalState(
    val snapshot: PeriodDocument? = null,
    val pending: List<PendingPeriodOp> = emptyList(),
    val nextSeq: Long = 1,
)

/**
 * 经期同步状态持久化，键名包含服务地址，切换地址后不会把一台服务器的数据推到另一台。
 */
class PeriodSyncStorage(
    private val prefs: SharedPreferences,
) {
    companion object {
        private const val KEY_SNAPSHOT = "snapshot:"
        private const val KEY_PENDING = "pending:"
        private const val KEY_NEXT_SEQ = "next_seq:"

        fun fromContext(context: Context): PeriodSyncStorage = PeriodSyncStorage(
            context.getSharedPreferences("period_sync", Context.MODE_PRIVATE),
        )
    }

    fun load(serverUrl: String): PeriodLocalState {
        val snapshot =
            prefs.getString(KEY_SNAPSHOT + serverUrl, null)?.let { raw ->
                runCatching { PeriodJson.decodeDocument(JSONObject(raw)) }.getOrNull()
            }
        val pending =
            prefs.getString(KEY_PENDING + serverUrl, null)?.let { raw ->
                runCatching {
                    val array = JSONArray(raw)
                    (0 until array.length()).mapNotNull { index ->
                        val item = array.getJSONObject(index)
                        PeriodJson.decodeOp(item.getJSONObject("op"))?.let { PendingPeriodOp(item.getLong("seq"), it) }
                    }
                }.getOrNull()
            } ?: emptyList()
        val nextSeq = maxOf(prefs.getLong(KEY_NEXT_SEQ + serverUrl, 1), (pending.maxOfOrNull { it.seq } ?: 0) + 1)
        return PeriodLocalState(snapshot, pending, nextSeq)
    }

    fun save(
        serverUrl: String,
        state: PeriodLocalState,
    ) {
        val pending =
            JSONArray(
                state.pending.map { JSONObject().put("seq", it.seq).put("op", PeriodJson.encodeOp(it.op)) },
            )
        val editor = prefs.edit()
        state.snapshot?.let { editor.putString(KEY_SNAPSHOT + serverUrl, PeriodJson.encodeDocument(it).toString()) }
            ?: editor.remove(KEY_SNAPSHOT + serverUrl)
        editor
            .putString(KEY_PENDING + serverUrl, pending.toString())
            .putLong(KEY_NEXT_SEQ + serverUrl, state.nextSeq)
            .apply()
    }
}
