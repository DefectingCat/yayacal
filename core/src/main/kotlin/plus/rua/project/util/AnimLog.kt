package plus.rua.project.util

import android.util.Log
import plus.rua.project.shared.BuildConfig

@Suppress("NOTHING_TO_INLINE")
inline fun logd(
    tag: String,
    message: () -> String,
) {
    if (BuildConfig.DEBUG) {
        try {
            Log.d(tag, message())
        } catch (_: RuntimeException) {
            // Android Log not available in JVM unit tests; fallback to stdout
            println("D/$tag: ${message()}")
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
inline fun logd(
    tag: String,
    message: String,
) {
    if (BuildConfig.DEBUG) {
        try {
            Log.d(tag, message)
        } catch (_: RuntimeException) {
            println("D/$tag: $message")
        }
    }
}
