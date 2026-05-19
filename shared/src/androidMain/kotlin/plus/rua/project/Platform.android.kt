package plus.rua.project

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

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
    val navState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navState,
        isBackEnabled = enabled,
        onBackCancelled = onCancel,
        onBackCompleted = onBack
    )

    LaunchedEffect(navState.transitionState) {
        val ts = navState.transitionState
        if (ts is NavigationEventTransitionState.InProgress) {
            onProgress(ts.latestEvent.progress)
        }
    }

    // 降级：部分设备（如 OPPO/ColorOS）不通过 OnBackInvokedCallback 分发返回事件
    BackHandler(enabled = enabled) {
        onBack()
    }
}
