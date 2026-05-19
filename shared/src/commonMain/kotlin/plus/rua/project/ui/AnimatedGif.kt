package plus.rua.project.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.panpf.sketch.AsyncImage

/**
 * 显示动画 GIF 图片。
 *
 * @param modifier 应用于图片的 Modifier
 * @param contentDescription 无障碍描述
 */
@Composable
fun AnimatedGif(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    AsyncImage(
        uri = "compose.resource://files/puppy_1.gif",
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
