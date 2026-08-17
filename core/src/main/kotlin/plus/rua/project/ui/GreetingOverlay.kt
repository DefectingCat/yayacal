package plus.rua.project.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.rua.project.BirthdayChecker
import plus.rua.project.QixiChecker
import plus.rua.project.shared.R as CoreR

private val GreetingFontFamily = FontFamily(Font(CoreR.font.zcool_kuaile))

private val GreetingTextColor = Color(0xFFE8554B)

private val GreetingExitFadeMillis = 300

/** M3 emphasized decelerate（compose 1.11 未提供常量，按 md.sys.motion token 手写）。 */
private val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

/**
 * 七夕问候遮罩：七夕当天（农历七月初七，闰七月不算）每次冷启动后全屏盖在主界面上。
 *
 * 壁纸铺满全屏并居中裁剪，顶部展示「七夕快乐！」艺术字（站酷快乐体子集，OFL）。
 * 点击任意处或按返回键以 300ms 淡出退出；不点击则一直展示。非七夕日期不渲染任何内容。
 * 可见状态随进程存续：旋转等配置变更不会重新弹出，冷启动重新判定。
 *
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun QixiGreetingOverlay(modifier: Modifier = Modifier) {
    GreetingOverlayShell(
        active = QixiChecker().isQixiToday(),
        wallpaper = CoreR.drawable.qixi_wallpaper,
        modifier = modifier,
    ) {
        Text(
            text = "七夕快乐！",
            color = GreetingTextColor,
            fontFamily = GreetingFontFamily,
            fontSize = 60.sp,
            style =
            TextStyle(
                shadow =
                Shadow(
                    color = Color(0x33000000),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f,
                ),
            ),
        )
    }
}

/**
 * 生日问候遮罩：每年公历 9 月 14 日每次冷启动后全屏盖在主界面上。
 *
 * 壁纸（小熊与钞票雨）铺满全屏并居中裁剪，顶部依次入场：金色生日皇冠自上方
 * 轻落 + 「生日快乐！」艺术字（站酷快乐体子集，OFL）自下方浮起，均以
 * graphicsLayer 驱动，不触发布局。点击任意处或按返回键以 300ms 淡出退出；
 * 不点击则一直展示。非生日日期不渲染任何内容。
 * 可见状态随进程存续：旋转等配置变更不会重新弹出（但入场动画会重放一次），冷启动重新判定。
 *
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun BirthdayGreetingOverlay(modifier: Modifier = Modifier) {
    val crownProgress = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            delay(80)
            crownProgress.animateTo(1f, tween(420, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(220)
            textProgress.animateTo(1f, tween(480, easing = EmphasizedDecelerateEasing))
        }
    }

    GreetingOverlayShell(
        active = BirthdayChecker().isBirthdayToday(),
        wallpaper = CoreR.drawable.birthday_wallpaper,
        topPadding = 52.dp,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(CoreR.drawable.ic_birthday_crown),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier =
            Modifier
                .size(56.dp)
                .graphicsLayer {
                    alpha = crownProgress.value
                    translationY = (crownProgress.value - 1f) * 12.dp.toPx()
                    scaleX = 0.6f + 0.4f * crownProgress.value
                    scaleY = 0.6f + 0.4f * crownProgress.value
                },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "生日快乐！",
            color = GreetingTextColor,
            fontFamily = GreetingFontFamily,
            fontSize = 60.sp,
            style =
            TextStyle(
                shadow =
                Shadow(
                    color = Color(0x33000000),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f,
                ),
            ),
            modifier =
            Modifier.graphicsLayer {
                alpha = textProgress.value
                translationY = (1f - textProgress.value) * 20.dp.toPx()
            },
        )
    }
}

/**
 * 问候遮罩共享骨架：节日当天冷启动后盖在主界面上的全屏壁纸 + 顶部问候内容。
 *
 * 点击任意处或按返回键以 300ms 淡出退出；不点击则一直展示；非当天不渲染任何内容。
 * 可见状态随进程存续（[rememberSaveable]），旋转等配置变更不会重新弹出，冷启动重新判定。
 *
 * @param active 今天是否展示该问候（仅冷启动时判定一次）
 * @param wallpaper 全屏壁纸 drawable，居中裁剪铺满
 * @param topPadding 顶部问候内容距状态栏的额外留白
 * @param modifier 外部传入的 Modifier
 * @param content 顶部居中的问候内容（艺术字等）
 */
@Composable
private fun GreetingOverlayShell(
    active: Boolean,
    wallpaper: Int,
    topPadding: Dp = 64.dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(active) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(GreetingExitFadeMillis)),
    ) {
        BackHandler { visible = false }

        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { visible = false },
        ) {
            Image(
                painter = painterResource(wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Column(
                modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = topPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}
