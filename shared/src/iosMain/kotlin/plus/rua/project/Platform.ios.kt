package plus.rua.project

import androidx.compose.runtime.Composable
import platform.UIKit.UIDevice

class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getGifUri(gifFile: String): String = "compose.resource://files/$gifFile"

actual fun getAppIconUri(): String = "compose.resource://files/app_icon.png?v=2"

@Composable
actual fun PredictiveBackHandler(
    enabled: Boolean,
    onProgress: (Float) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    // iOS 没有预测性返回手势，由导航栏按钮处理
}
