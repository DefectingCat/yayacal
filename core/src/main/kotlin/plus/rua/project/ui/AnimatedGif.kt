package plus.rua.project.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.request.repeatCount
import plus.rua.project.getWebpUri

/**
 * WebP 动画文件名列表（001.webp ~ 152.webp）。
 */
private val WEBP_FILES = (1..152).map { "${it.toString().padStart(3, '0')}.webp" }

private const val REPEAT_COUNT = 2

/**
 * 显示动画 WebP 图片，切换日期时随机选择一个。
 *
 * 动画播放 3 次（1 + [REPEAT_COUNT]）后停止，避免持续解码导致的帧丢失。
 *
 * @param modifier 应用于图片的 Modifier
 * @param contentDescription 无障碍描述
 * @param seed 用于控制重新随机时机的 key，变化时重新选择 WebP
 */
@Composable
fun AnimatedGif(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    seed: Any? = null,
) {
    val webpFile = remember(seed) { WEBP_FILES.random() }
    val uri = remember(webpFile) { getWebpUri(webpFile) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(seed) {
        alpha.snapTo(0f)
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(150, easing = FastOutSlowInEasing),
        )
    }

    val state = rememberAsyncImageState(
        options = remember { ImageOptions { repeatCount(REPEAT_COUNT) } }
    )

    AsyncImage(
        uri = uri,
        contentDescription = contentDescription,
        state = state,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha.value
        },
    )
}
