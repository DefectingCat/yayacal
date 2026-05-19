package plus.rua.project

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import plus.rua.project.ui.AboutScreen
import plus.rua.project.ui.CalendarMonthView
import plus.rua.project.ui.LicensesScreen

private enum class Screen { Main, About, Licenses }

/**
 * 应用入口 Composable，根据系统主题切换明暗 ColorScheme 并管理页面导航。
 */
@Composable
@Preview(name = "Calendar App")
fun App() {
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    var backProgress by remember { mutableFloatStateOf(0f) }

    val handleBack: () -> Unit = {
        backProgress = 0f
        when (currentScreen) {
            Screen.About -> currentScreen = Screen.Main
            Screen.Licenses -> currentScreen = Screen.About
            else -> {}
        }
    }

    val handleCancel: () -> Unit = {
        backProgress = 0f
    }

    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    // 正向导航：新页面从右侧滑入覆盖，旧页面略微左移+淡出
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                            (slideOutHorizontally { -it / 4 } + fadeOut())
                } else {
                    // 返回导航：新页面从左侧滑入，旧页面向右侧滑出
                    (slideInHorizontally(animationSpec = tween(250)) { -it } + fadeIn(
                        animationSpec = tween(
                            250
                        )
                    )) togetherWith
                            (slideOutHorizontally(animationSpec = tween(250)) { it } + fadeOut(
                                animationSpec = tween(250)
                            ))
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { screen ->
            when (screen) {
                Screen.Main -> CalendarMonthView(
                    modifier = Modifier,
                    onNavigateToAbout = { currentScreen = Screen.About }
                )

                Screen.About -> {
                    PredictiveBackHandler(
                        enabled = backProgress == 0f,
                        onProgress = { backProgress = it },
                        onBack = handleBack,
                        onCancel = handleCancel
                    )
                    AboutScreen(
                        onBack = { currentScreen = Screen.Main },
                        onNavigateToLicenses = { currentScreen = Screen.Licenses },
                        modifier = Modifier.graphicsLayer {
                            translationX = backProgress * size.width * 0.3f
                            scaleX = 1f - backProgress * 0.05f
                            scaleY = 1f - backProgress * 0.05f
                        }
                    )
                }

                Screen.Licenses -> {
                    PredictiveBackHandler(
                        enabled = backProgress == 0f,
                        onProgress = { backProgress = it },
                        onBack = handleBack,
                        onCancel = handleCancel
                    )
                    LicensesScreen(
                        onBack = { currentScreen = Screen.About },
                        modifier = Modifier.graphicsLayer {
                            translationX = backProgress * size.width * 0.3f
                            scaleX = 1f - backProgress * 0.05f
                            scaleY = 1f - backProgress * 0.05f
                        }
                    )
                }
            }
        }
    }
}
