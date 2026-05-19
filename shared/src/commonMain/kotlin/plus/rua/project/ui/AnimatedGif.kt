package plus.rua.project.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.panpf.sketch.AsyncImage
import plus.rua.project.getGifUri

/**
 * GIF 文件名列表（001.gif ~ 152.gif）。
 */
private val GIF_FILES = (1..152).map { String.format("%03d.gif", it) }

/**
 * 显示动画 GIF 图片，切换日期时随机选择一个。
 *
 * @param modifier 应用于图片的 Modifier
 * @param contentDescription 无障碍描述
 * @param seed 用于控制重新随机时机的 key，变化时重新选择 GIF
 */
@Composable
fun AnimatedGif(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    seed: Any? = null,
) {
    val gifFile = remember(seed) { GIF_FILES.random() }
    val uri = remember(gifFile) { getGifUri(gifFile) }
    val scale = remember { Animatable(0f) }

    LaunchedEffect(seed) {
        scale.snapTo(0f)
        scale.animateTo(
            targetValue = 1.1f,
            animationSpec = tween(250, easing = FastOutSlowInEasing),
        )
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        )
    }

    AsyncImage(
        uri = uri,
        contentDescription = contentDescription,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = scale.value.coerceIn(0f, 1f)
            },
    )
}
