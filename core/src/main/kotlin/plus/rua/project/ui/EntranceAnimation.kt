package plus.rua.project.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 单卡入场时长（M3 emphasized 进场档位 400~450ms）。 */
private const val ENTRANCE_DURATION_MS = 450

/** 相邻卡片错峰间隔。 */
private const val ENTRANCE_STAGGER_MS = 60L

/** 上滑起始偏移（dp 数值，避免重复分配 Dp）。 */
private const val ENTRANCE_OFFSET_DP = 24f

/** M3 emphasized decelerate 缓动（md.sys.motion.easing.emphasized.decelerate），进场元素专用。 */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/**
 * M3 级联入场动画：淡入 + 上滑，按 [index] 错峰（emphasized decelerate 缓动）。
 *
 * 通过 [graphicsLayer] 只改 alpha/translationY，不触发 layout pass；
 * 动画在首次组合时播放一次，重组不重复触发。
 *
 * @param index 卡片在列表中的序号，决定错峰延迟（0 立即开始）
 */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val alpha = remember { Animatable(0f) }
    val offsetDp = remember { Animatable(ENTRANCE_OFFSET_DP) }
    LaunchedEffect(Unit) {
        delay(ENTRANCE_STAGGER_MS * index)
        launch {
            alpha.animateTo(1f, tween(ENTRANCE_DURATION_MS, easing = EmphasizedDecelerate))
        }
        launch {
            offsetDp.animateTo(0f, tween(ENTRANCE_DURATION_MS, easing = EmphasizedDecelerate))
        }
    }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = offsetDp.value.dp.toPx()
    }
}
