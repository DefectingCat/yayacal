package plus.rua.project

import androidx.compose.runtime.Composable

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * 获取 GIF 资源的 URI。
 *
 * @param gifFile GIF 文件名（如 "001.gif"）
 * @return 平台特定的资源 URI
 */
expect fun getGifUri(gifFile: String): String

expect fun getAppIconUri(): String

@Composable
expect fun getAppVersion(): String

/**
 * 预测性返回手势处理器（Android 13+）。
 *
 * @param enabled 是否启用
 * @param onProgress 手势进度回调（0.0~1.0），跟手过程中持续调用
 * @param onBack 手势完成回调（滑动距离足够，执行返回）
 * @param onCancel 手势取消回调（滑动距离不足，回弹）
 */
@Composable
expect fun PredictiveBackHandler(
    enabled: Boolean = true,
    onProgress: (Float) -> Unit = {},
    onBack: () -> Unit,
    onCancel: () -> Unit = {}
)
