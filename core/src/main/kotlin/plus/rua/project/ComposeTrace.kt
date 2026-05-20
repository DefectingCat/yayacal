package plus.rua.project

import android.os.Trace

/**
 * Systrace 包装，用于录制 Compose 性能 trace。
 * Android 实际调用 android.os.Trace。
 */
fun composeTraceBeginSection(name: String) {
    try {
        Trace.beginSection(name)
    } catch (_: RuntimeException) {
        // Trace API 在 host test 中未 stub；忽略
    }
}

fun composeTraceEndSection() {
    try {
        Trace.endSection()
    } catch (_: RuntimeException) {
        // Trace API 在 host test 中未 stub；忽略
    }
}
