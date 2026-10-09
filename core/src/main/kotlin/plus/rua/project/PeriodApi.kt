package plus.rua.project

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** 后端经期文档接口；数据不分账号，请求不带 `X-Account-ID`。 */
interface PeriodApi {
    suspend fun fetch(): PeriodDocument

    /** 以 [document] 的 revision 作为 base_revision 整份替换，返回服务器保存后的文档。 */
    suspend fun replace(document: PeriodDocument): PeriodDocument
}

/** 非 2xx 响应；[status] 409 表示其他设备已更新，404 通常是旧版后端。 */
class PeriodApiException(
    val status: Int,
    message: String,
) : IOException(message)

class HttpPeriodApi(
    private val baseUrl: String,
) : PeriodApi {
    override suspend fun fetch(): PeriodDocument = PeriodJson.decodeDocument(JSONObject(request("GET", null)))

    override suspend fun replace(document: PeriodDocument): PeriodDocument {
        val body = PeriodJson.encodeInput(document).toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        return PeriodJson.decodeDocument(JSONObject(request("PUT", body)))
    }

    private suspend fun request(
        method: String,
        body: RequestBody?,
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$baseUrl/api/v1/period").method(method, body).build()
        val call = client.newCall(request)
        val response =
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(IOException("连接失败，请检查网络或服务地址", e))
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            continuation.resume(response) { _, value, _ -> value.close() }
                        }
                    },
                )
            }
        response.use {
            val text = it.body.string()
            if (!it.isSuccessful) {
                val message = runCatching { JSONObject(text).getString("error") }.getOrDefault("请求失败（${it.code}）")
                throw PeriodApiException(it.code, message)
            }
            text
        }
    }

    private companion object {
        val client: OkHttpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .followRedirects(false)
                .build()
    }
}

/** 经期文档与待同步操作的 JSON 编解码，字段名与后端约定一致。 */
internal object PeriodJson {
    fun encodeDocument(document: PeriodDocument): JSONObject = encodeContent(document).put("revision", document.revision)

    fun encodeInput(document: PeriodDocument): JSONObject = encodeContent(document).put("base_revision", document.revision)

    fun decodeDocument(json: JSONObject): PeriodDocument {
        val settings = json.getJSONObject("settings")
        return PeriodDocument(
            revision = json.getLong("revision"),
            settings =
            PeriodSettings(
                cycleLength = settings.getInt("cycle_length"),
                periodLength = settings.getInt("period_length"),
                lutealLength = settings.getInt("luteal_length"),
            ),
            ranges =
            json.getJSONArray("ranges").objects().map {
                PeriodRange(LocalDate.parse(it.getString("start")), it.optionalDate("end"))
            },
            notes =
            json.getJSONArray("notes").objects().map {
                PeriodNote(
                    date = LocalDate.parse(it.getString("date")),
                    mood = PeriodMood.fromKey(it.optionalString("mood")),
                    text = it.optString("text", ""),
                )
            },
        )
    }

    fun encodeOp(op: PeriodOp): JSONObject = when (op) {
        is PeriodOp.StartPeriod -> {
            JSONObject().put("type", "start").put("date", op.date.toString())
        }

        is PeriodOp.EndPeriod -> {
            JSONObject().put("type", "end").put("date", op.date.toString())
        }

        is PeriodOp.SetPeriodDay -> {
            JSONObject()
                .put("type", "day")
                .put("date", op.date.toString())
                .put("period", op.isPeriod)
                .put("today", op.today.toString())
        }

        is PeriodOp.UpdateRange -> {
            JSONObject()
                .put("type", "update")
                .put("old_start", op.oldStart.toString())
                .put("start", op.start.toString())
                .put("end", op.end?.toString() ?: JSONObject.NULL)
        }

        is PeriodOp.DeleteRange -> {
            JSONObject().put("type", "delete").put("start", op.start.toString())
        }

        is PeriodOp.SetNote -> {
            JSONObject()
                .put("type", "note")
                .put("date", op.date.toString())
                .put("mood", op.mood?.key ?: JSONObject.NULL)
                .put("text", op.text)
        }

        is PeriodOp.UpdateSettings -> {
            JSONObject().put("type", "settings").put("settings", encodeSettings(op.settings))
        }
    }

    /** 无法识别的操作返回 null，由调用方丢弃。 */
    fun decodeOp(json: JSONObject): PeriodOp? = runCatching {
        when (json.getString("type")) {
            "start" -> {
                PeriodOp.StartPeriod(json.date("date"))
            }

            "end" -> {
                PeriodOp.EndPeriod(json.date("date"))
            }

            "day" -> {
                PeriodOp.SetPeriodDay(json.date("date"), json.getBoolean("period"), json.date("today"))
            }

            "update" -> {
                PeriodOp.UpdateRange(json.date("old_start"), json.date("start"), json.optionalDate("end"))
            }

            "delete" -> {
                PeriodOp.DeleteRange(json.date("start"))
            }

            "note" -> {
                PeriodOp.SetNote(json.date("date"), PeriodMood.fromKey(json.optionalString("mood")), json.getString("text"))
            }

            "settings" -> {
                val settings = json.getJSONObject("settings")
                PeriodOp.UpdateSettings(
                    PeriodSettings(
                        cycleLength = settings.getInt("cycle_length"),
                        periodLength = settings.getInt("period_length"),
                        lutealLength = settings.getInt("luteal_length"),
                    ),
                )
            }

            else -> {
                null
            }
        }
    }.getOrNull()

    private fun encodeContent(document: PeriodDocument): JSONObject = JSONObject()
        .put("settings", encodeSettings(document.settings))
        .put(
            "ranges",
            JSONArray(
                document.ranges.map {
                    JSONObject().put("start", it.start.toString()).put("end", it.end?.toString() ?: JSONObject.NULL)
                },
            ),
        ).put(
            "notes",
            JSONArray(
                document.notes.map {
                    JSONObject()
                        .put("date", it.date.toString())
                        .put("mood", it.mood?.key ?: JSONObject.NULL)
                        .put("text", it.text)
                },
            ),
        )

    private fun encodeSettings(settings: PeriodSettings): JSONObject = JSONObject()
        .put("cycle_length", settings.cycleLength)
        .put("period_length", settings.periodLength)
        .put("luteal_length", settings.lutealLength)

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.date(key: String): LocalDate = LocalDate.parse(getString(key))

    private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.optionalDate(key: String): LocalDate? = optionalString(key)?.let(LocalDate::parse)
}
