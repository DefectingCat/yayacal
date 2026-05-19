package plus.rua.project

import android.os.Build
import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getGifUri(gifFile: String): String = "file:///android_asset/gifs/$gifFile"

actual fun getAppIconUri(): String = "file:///android_asset/app_icon.png?v=2"

@Composable
actual fun getAppVersion(): String {
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

@Composable
actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
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
