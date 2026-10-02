@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.panpf.sketch.rememberAsyncImageState
import com.github.panpf.sketch.request.ImageOptions
import com.github.panpf.sketch.state.ThumbnailMemoryCacheStateImage

// 页面间图片尺寸不同也可先显示同一 URI 已解码的图片，避免封面、头像和缩略图闪成空块。
internal val momentsImageOptions = ImageOptions { placeholder(ThumbnailMemoryCacheStateImage()) }

/** 每个图片组件独立持有加载状态，共用跨尺寸的缓存占位规则。 */
@Composable
internal fun rememberMomentsImageState() = rememberAsyncImageState(options = momentsImageOptions)

/** 顶部轻量加载指示器，随下拉显示，刷新时旋转；避开状态栏和导航按钮。 */
@Composable
internal fun MomentsRefreshIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    color: Color,
    containerColor: Color,
    belowTopBar: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val fraction = state.distanceFraction.coerceIn(0f, 1f)
    if (!isRefreshing && fraction <= 0f) return
    Box(
        modifier
            .then(if (belowTopBar) Modifier else Modifier.statusBarsPadding())
            .padding(top = if (belowTopBar) 12.dp else 64.dp)
            .graphicsLayer { alpha = if (isRefreshing) 1f else fraction }
            .size(32.dp)
            .background(containerColor, CircleShape)
            .testTag("moments_loading_indicator"),
        contentAlignment = Alignment.Center,
    ) {
        MomentsLoadingSpinner(isSpinning = isRefreshing, progress = fraction, color = color)
    }
}

/** 微信风格的十二辐条转圈，分页时也复用同一视觉。 */
@Composable
internal fun MomentsLoadingSpinner(
    isSpinning: Boolean = true,
    progress: Float = 1f,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
) {
    val rotation = if (isSpinning) {
        val transition = rememberInfiniteTransition(label = "moments_loading")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "moments_loading_rotation",
        )
        angle
    } else {
        progress * 360f
    }
    Canvas(
        modifier.size(20.dp).semantics {
            contentDescription = "正在加载"
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
    ) {
        repeat(12) { index ->
            rotate(rotation + index * 30f) {
                drawLine(
                    color = color.copy(alpha = color.alpha * (index + 1) / 12f),
                    start = Offset(center.x, size.height * 0.08f),
                    end = Offset(center.x, size.height * 0.27f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 首次请求尚未完成时保留列表结构，避免用“暂无动态”的插画替代待加载内容。 */
@Composable
internal fun MomentsLoadingContent(modifier: Modifier = Modifier) {
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp).testTag("moments_loading_placeholder"),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(placeholderColor, shape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.fillMaxWidth(0.25f).height(12.dp).background(placeholderColor, shape))
                    Box(Modifier.fillMaxWidth(0.8f).height(12.dp).background(placeholderColor, shape))
                    Box(Modifier.fillMaxWidth(0.6f).height(12.dp).background(placeholderColor, shape))
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
