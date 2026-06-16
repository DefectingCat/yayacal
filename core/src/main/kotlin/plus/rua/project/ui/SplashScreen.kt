package plus.rua.project.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import plus.rua.project.shared.R as CoreR

private val SplashIconSize = 80.dp

/**
 * 品牌启动页 UI。
 *
 * 背景图铺满全屏，Light/Dark 资源由 Android 根据配置自动选择。
 * app icon 水平居中，其垂直中心位于状态栏下方可用区域高度的约 25% 处。
 * 图片仅用于装饰，因此 contentDescription 为 null。
 *
 * @param iconPainter app icon 的 Painter
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun SplashScreen(
    iconPainter: Painter,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp.dp
    val statusBarHeight = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val availableHeight = screenHeightDp - statusBarHeight
    val iconTopPadding = statusBarHeight + (availableHeight * 0.25f) - (SplashIconSize / 2)

    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(CoreR.drawable.launch_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Image(
            painter = iconPainter,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = iconTopPadding.coerceAtLeast(statusBarHeight))
                .size(SplashIconSize),
        )
    }
}
