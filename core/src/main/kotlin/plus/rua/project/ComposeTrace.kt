package plus.rua.project

import android.os.Trace
import plus.rua.project.shared.BuildConfig

/**
 * Systrace 包装，用于录制 Compose 性能 trace。
 * 由 [BuildConfig.ENABLE_TRACE] 控制开关，release 构建默认关闭，trace 构建开启。
 */
fun composeTraceBeginSection(name: String) {
    if (!BuildConfig.ENABLE_TRACE) return
    try {
        Trace.beginSection(name)
    } catch (_: RuntimeException) {
        // Trace API 在 host test 中未 stub；忽略
    }
}

fun composeTraceEndSection() {
    if (!BuildConfig.ENABLE_TRACE) return
    try {
        Trace.endSection()
    } catch (_: RuntimeException) {
        // Trace API 在 host test 中未 stub；忽略
    }
}
