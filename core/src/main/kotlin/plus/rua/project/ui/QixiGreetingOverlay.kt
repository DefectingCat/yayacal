package plus.rua.project.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import plus.rua.project.QixiChecker
import plus.rua.project.shared.R as CoreR

private val QixiFontFamily = FontFamily(Font(CoreR.font.zcool_kuaile))

private val QixiTextColor = Color(0xFFE8554B)
private val QixiExitFadeMillis = 300

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
    var visible by rememberSaveable { mutableStateOf(QixiChecker().isQixiToday()) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = EnterTransition.None,
        exit = fadeOut(animationSpec = tween(QixiExitFadeMillis)),
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
                painter = painterResource(CoreR.drawable.qixi_wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Text(
                text = "七夕快乐！",
                color = QixiTextColor,
                fontFamily = QixiFontFamily,
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
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            )
        }
    }
}
