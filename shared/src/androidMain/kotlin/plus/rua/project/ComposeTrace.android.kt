package plus.rua.project

import android.os.Trace

actual fun composeTraceBeginSection(name: String) {
    try {
        Trace.beginSection(name)
    } catch (_: RuntimeException) {
        // Trace API 在 host test 中未 stub；忽略
    }
}

actual fun composeTraceEndSection() {
    try {
        Trace.endSection()
    } catch (_: RuntimeException) {
        // Trace API 在 host test 中未 stub；忽略
    }
}