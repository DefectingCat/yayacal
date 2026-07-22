package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.zoomimage.SketchZoomAsyncImage
import com.github.panpf.zoomimage.rememberSketchZoomState

/**
 * 全屏图片灯箱，支持双指缩放、平移与旋转查看。
 *
 * 在全屏深色遮罩中居中显示图片，底层用 zoomimage 的 [SketchZoomAsyncImage]
 * 提供手势：双指缩放、单指平移、双指捻合自由旋转（松手后吸附到最近的
 * 0/90/180/270°）、双击复位。点击图片外的空白区域或按返回键触发 [onDismiss]。
 *
 * sketch 的 [rememberAsyncImageState] 负责加载，zoomimage 的
 * [rememberSketchZoomState] 负责缩放/平移/旋转——后者是 sketch `AsyncImage`
 * 之外新增的必需参数。
 *
 * @param photoUri 图片 URI；为 null 时直接返回不渲染
 * @param contentDescription 无障碍描述，传 null 表示纯装饰
 * @param onDismiss 关闭回调（点击空白区域或按返回键时触发）
 * @param modifier 布局修饰符
 */
@Composable
fun ImageLightbox(
    photoUri: String?,
    contentDescription: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (photoUri == null) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            val asyncImageState = rememberAsyncImageState()
            val zoomState = rememberSketchZoomState()
            SketchZoomAsyncImage(
                uri = photoUri,
                contentDescription = contentDescription,
                state = asyncImageState,
                zoomState = zoomState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
