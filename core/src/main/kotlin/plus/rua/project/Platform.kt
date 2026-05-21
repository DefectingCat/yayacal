package plus.rua.project

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * 获取 GIF 资源的 URI。
 *
 * @param gifFile GIF 文件名（如 "001.gif"）
 * @return 平台特定的资源 URI
 */
fun getGifUri(gifFile: String): String = "file:///android_asset/gifs/$gifFile"

fun getAppIconUri(): String = "file:///android_asset/app_icon.png?v=2"

@Composable
fun getAppVersion(): String {
    val context = LocalContext.current.applicationContext
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }
}

/**
 * 预测性返回手势处理器（Android 13+）。
 *
 * @param enabled 是否启用
 * @param onProgress 手势进度回调（0.0~1.0），跟手过程中持续调用
 * @param onBack 手势完成回调（滑动距离足够，执行返回）
 * @param onCancel 手势取消回调（滑动距离不足，回弹）
 */
@Composable
fun AppPredictiveBackHandler(
    enabled: Boolean = true,
    onProgress: (Float) -> Unit = {},
    onBack: () -> Unit,
    onCancel: () -> Unit = {}
) {
    // 官方 PredictiveBackHandler — Flow 模式：collect 完成=返回，CancellationException=取消
    PredictiveBackHandler(enabled = enabled) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { event ->
                onProgress(event.progress)
            }
            onBack()
        } catch (e: CancellationException) {
            onCancel()
        }
    }

    // 降级：部分设备（如 OPPO/ColorOS）不通过 OnBackInvokedCallback 分发返回事件
    BackHandler(enabled = enabled) {
        onBack()
    }
}
