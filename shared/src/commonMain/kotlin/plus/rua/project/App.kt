package plus.rua.project

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import plus.rua.project.ui.AboutScreen
import plus.rua.project.ui.CalendarMonthView
import plus.rua.project.ui.LicensesScreen
import plus.rua.project.ui.lerp

private enum class Screen { Main, About, Licenses }

/** 返回手势动画：顶层页面滑出 + 淡出 + 缩小 + 圆角阴影 */
private fun GraphicsLayerScope.applyDismissTransform(progress: Float) {
    // 二次缓动：小幅手势产生更柔和的视觉变化，大幅手势仍达到完整效果
    val p = progress * progress
    translationX = p * size.width * 0.5f
    scaleX = 1f - p * 0.08f
    scaleY = 1f - p * 0.08f
    alpha = 1f - p
    shadowElevation = 32.dp.toPx() * p
    shape = RoundedCornerShape(28.dp * p)
    clip = p > 0.01f
}

/** 底层页面缩放：随返回进度从 baseScale 放大到 1.0 */
private fun GraphicsLayerScope.applyRevealTransform(
    progress: Float,
    forwardProgress: Float,
    isForwardAnimating: Boolean
) {
    val p = progress * progress
    val baseScale = 0.92f + 0.08f * p
    val scale = if (isForwardAnimating) lerp(1f, baseScale, forwardProgress) else baseScale
    scaleX = scale
    scaleY = scale
}

/** 前向导航动画：新页面从右侧滑入 */
private fun GraphicsLayerScope.applyEnterTransform(progress: Float) {
    translationX = (1f - progress) * size.width
    alpha = progress
}

/**
 * 应用入口 Composable，根据系统主题切换明暗 ColorScheme 并管理页面导航。
 *
 * 使用 Box 分层布局替代 AnimatedContent，支持预测性返回手势：
 * - 底层页面始终组合（状态保持），缩放显现
 * - 顶层页面在手势期间平滑位移、缩放、圆角、阴影
 * - 前向导航从右侧滑入，返回导航跟手驱动
 */
@Composable
@Preview(name = "Calendar App")
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    val backAnimProgress = remember { Animatable(0f) }
    val effectiveBackProgress by remember {
        derivedStateOf { maxOf(backProgress, backAnimProgress.value) }
    }

    var forwardTarget by remember { mutableStateOf<Screen?>(null) }
    val forwardProgress = remember { Animatable(1f) }

    var isHandlingBack by remember { mutableStateOf(false) }
    val handleBack: () -> Unit = lambda@{
        if (isHandlingBack) return@lambda
        isHandlingBack = true
        scope.launch {
            backAnimProgress.snapTo(backProgress)
            backProgress = 0f
            backAnimProgress.animateTo(
                1f,
                spring(
                    stiffness = Spring.StiffnessMedium,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
            currentScreen = when (currentScreen) {
                Screen.About -> Screen.Main
                Screen.Licenses -> Screen.About
                else -> currentScreen
            }
            backAnimProgress.snapTo(0f)
            isHandlingBack = false
        }
    }

    val handleCancel: () -> Unit = {
        scope.launch {
            backAnimProgress.snapTo(backProgress)
            backProgress = 0f
            backAnimProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    val navigateTo: (Screen) -> Unit = { target ->
        if (forwardTarget == null) {
            scope.launch {
                forwardTarget = target
                currentScreen = target
                forwardProgress.snapTo(0f)
                forwardProgress.animateTo(1f, tween(350, easing = FastOutSlowInEasing))
                forwardTarget = null
            }
        }
    }

    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Layer 0: CalendarMonthView（始终组合以保持状态）
            CalendarMonthView(
                modifier = Modifier.graphicsLayer {
                    if (currentScreen != Screen.Main) {
                        applyRevealTransform(
                            effectiveBackProgress,
                            forwardProgress.value,
                            forwardTarget != null
                        )
                    }
                },
                onNavigateToAbout = { navigateTo(Screen.About) }
            )

            // Layer 1: AboutScreen（About 或 Licenses 页面时组合）
            if (currentScreen == Screen.About || currentScreen == Screen.Licenses) {
                AboutScreen(
                    onBack = {
                        if (currentScreen == Screen.About) handleBack()
                    },
                    onNavigateToLicenses = {
                        if (currentScreen == Screen.About) navigateTo(Screen.Licenses)
                    },
                    modifier = Modifier.graphicsLayer {
                        when (currentScreen) {
                            Screen.Licenses -> applyRevealTransform(
                                effectiveBackProgress,
                                forwardProgress.value,
                                forwardTarget == Screen.Licenses
                            )

                            Screen.About -> {
                                val bp = effectiveBackProgress
                                val fp = forwardProgress.value
                                when {
                                    bp > 0.001f -> applyDismissTransform(bp)
                                    fp < 0.999f && forwardTarget == Screen.About -> applyEnterTransform(
                                        fp
                                    )
                                }
                            }

                            else -> {}
                        }
                    }
                )
            }

            // Layer 2: LicensesScreen（Licenses 页面时组合）
            if (currentScreen == Screen.Licenses) {
                LicensesScreen(
                    onBack = handleBack,
                    modifier = Modifier.graphicsLayer {
                        val bp = effectiveBackProgress
                        val fp = forwardProgress.value
                        when {
                            bp > 0.001f -> applyDismissTransform(bp)
                            fp < 0.999f && forwardTarget == Screen.Licenses -> applyEnterTransform(
                                fp
                            )
                        }
                    }
                )
            }

            // 预测性返回手势
            if (currentScreen != Screen.Main) {
                PredictiveBackHandler(
                    enabled = !backAnimProgress.isRunning && !isHandlingBack && forwardTarget == null,
                    onProgress = { backProgress = it },
                    onBack = handleBack,
                    onCancel = handleCancel
                )
            }
        }
    }
}
