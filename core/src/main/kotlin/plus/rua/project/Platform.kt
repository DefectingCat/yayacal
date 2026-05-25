package plus.rua.project

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 获取 WebP 动画资源的 URI。
 *
 * @param webpFile WebP 文件名（如 "001.webp"）
 * @return 平台特定的资源 URI
 */
fun getWebpUri(webpFile: String): String = "file:///android_asset/gifs/$webpFile"

fun getAppIconUri(): String = "file:///android_asset/app_icon.webp?v=2"

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
