package plus.rua.project

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CancellationException

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

actual fun getGifUri(gifFile: String): String = "file:///android_asset/gifs/$gifFile"

actual fun getAppIconUri(): String = "file:///android_asset/app_icon.png"

@Composable
actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= 34) {
        rememberCoroutineScope()
        androidx.activity.compose.PredictiveBackHandler(enabled) { progress ->
            try {
                progress.collect { backEvent ->
                    onProgress(backEvent.progress)
                }
                onBack()
            } catch (e: CancellationException) {
                onCancel()
            }
        }
    } else {
        androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
    }
}
