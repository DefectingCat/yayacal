package plus.rua.project.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import plus.rua.project.BirthdayChecker
import plus.rua.project.ConfessionChecker
import plus.rua.project.QixiChecker
import kotlin.math.sin
import plus.rua.project.shared.R as CoreR

private val GreetingFontFamily = FontFamily(Font(CoreR.font.zcool_kuaile))

private val GreetingTextColor = Color(0xFFE5383B)

private val GreetingExitFadeMillis = 300

/** M3 emphasized decelerate（compose 1.11 未提供常量，按 md.sys.motion token 手写）。 */
private val EmphasizedDecelerateEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

private enum class ParticleType {
    STAR,
    CONFETTI_RECT,
    DOT,
    HEART,
}

private data class ConfettiParticle(
    val initialXRatio: Float,
    val initialYRatio: Float,
    val color: Color,
    val sizeDp: Float,
    val rotationDeg: Float,
    val rotationSpeed: Float,
    val floatSpeed: Float,
    val type: ParticleType,
    val phase: Float,
)

/**
 * 预设的生日庆祝粒子集合（彩带碎屑、闪烁星芒与柔光点）。
 */
private val PresetBirthdayParticles: List<ConfettiParticle> =
    listOf(
        ConfettiParticle(0.10f, 0.10f, Color(0xFFFFD166), 14f, 15f, 25f, 1.0f, ParticleType.STAR, 0.1f),
        ConfettiParticle(0.90f, 0.08f, Color(0xFFFF758F), 16f, -20f, -35f, 1.2f, ParticleType.STAR, 0.5f),
        ConfettiParticle(0.07f, 0.22f, Color(0xFF99E2B4), 10f, 45f, 50f, 0.8f, ParticleType.CONFETTI_RECT, 0.9f),
        ConfettiParticle(0.93f, 0.20f, Color(0xFFFFB4D9), 12f, -30f, -40f, 1.1f, ParticleType.CONFETTI_RECT, 0.3f),
        ConfettiParticle(0.16f, 0.04f, Color(0xFFF7A072), 8f, 0f, 15f, 0.7f, ParticleType.DOT, 0.7f),
        ConfettiParticle(0.84f, 0.04f, Color(0xFFD8BBFF), 9f, 0f, 15f, 0.9f, ParticleType.DOT, 0.2f),
        ConfettiParticle(0.06f, 0.38f, Color(0xFFFFE5B4), 13f, 25f, -30f, 1.0f, ParticleType.STAR, 0.8f),
        ConfettiParticle(0.94f, 0.36f, Color(0xFFFF8FA3), 15f, -45f, 35f, 1.3f, ParticleType.STAR, 0.4f),
        ConfettiParticle(0.14f, 0.48f, Color(0xFF80DED9), 11f, 60f, 40f, 0.9f, ParticleType.CONFETTI_RECT, 0.6f),
        ConfettiParticle(0.86f, 0.46f, Color(0xFFF4A261), 11f, -15f, -25f, 0.8f, ParticleType.CONFETTI_RECT, 0.0f),
        ConfettiParticle(0.24f, 0.42f, Color(0xFFFFD166), 7f, 0f, 12f, 0.6f, ParticleType.DOT, 0.5f),
        ConfettiParticle(0.76f, 0.40f, Color(0xFFFF758F), 8f, 0f, 12f, 0.7f, ParticleType.DOT, 0.3f),
        ConfettiParticle(0.10f, 0.62f, Color(0xFFD8BBFF), 14f, 35f, 20f, 1.0f, ParticleType.STAR, 0.2f),
        ConfettiParticle(0.90f, 0.60f, Color(0xFFFFE5B4), 12f, -10f, -20f, 0.9f, ParticleType.STAR, 0.7f),
        ConfettiParticle(0.05f, 0.76f, Color(0xFFFF8FA3), 10f, 50f, 30f, 0.8f, ParticleType.CONFETTI_RECT, 0.4f),
        ConfettiParticle(0.95f, 0.74f, Color(0xFF99E2B4), 12f, -55f, -35f, 1.1f, ParticleType.CONFETTI_RECT, 0.8f),
        ConfettiParticle(0.20f, 0.84f, Color(0xFFF7A072), 7f, 0f, 10f, 0.5f, ParticleType.DOT, 0.1f),
        ConfettiParticle(0.80f, 0.82f, Color(0xFFFFD166), 8f, 0f, 10f, 0.6f, ParticleType.DOT, 0.9f),
        ConfettiParticle(0.50f, 0.03f, Color(0xFFFFB4D9), 11f, 15f, -15f, 0.7f, ParticleType.STAR, 0.4f),
        ConfettiParticle(0.32f, 0.06f, Color(0xFF80DED9), 6f, 0f, 10f, 0.5f, ParticleType.DOT, 0.6f),
        ConfettiParticle(0.68f, 0.06f, Color(0xFFFF758F), 6f, 0f, 10f, 0.5f, ParticleType.DOT, 0.2f),
    )

/**
 * 预设的七夕浪漫粒子集合（心形、浪漫星芒、雨滴微光点）。
 */
private val PresetQixiParticles: List<ConfettiParticle> =
    listOf(
        ConfettiParticle(0.10f, 0.12f, Color(0xFFFF758F), 14f, 15f, 25f, 1.0f, ParticleType.HEART, 0.1f),
        ConfettiParticle(0.90f, 0.10f, Color(0xFFFFB4D9), 16f, -15f, -30f, 1.2f, ParticleType.HEART, 0.5f),
        ConfettiParticle(0.08f, 0.25f, Color(0xFFA2D2FF), 9f, 0f, 15f, 0.8f, ParticleType.DOT, 0.9f),
        ConfettiParticle(0.92f, 0.22f, Color(0xFFD8BBFF), 13f, 20f, 30f, 1.1f, ParticleType.STAR, 0.3f),
        ConfettiParticle(0.15f, 0.05f, Color(0xFFFFD166), 11f, 0f, 20f, 0.7f, ParticleType.STAR, 0.7f),
        ConfettiParticle(0.85f, 0.05f, Color(0xFFFF8FA3), 12f, -10f, -20f, 0.9f, ParticleType.HEART, 0.2f),
        ConfettiParticle(0.06f, 0.40f, Color(0xFFFFCCD5), 14f, 25f, -25f, 1.0f, ParticleType.HEART, 0.8f),
        ConfettiParticle(0.94f, 0.38f, Color(0xFFC77DFF), 12f, -40f, 35f, 1.3f, ParticleType.STAR, 0.4f),
        ConfettiParticle(0.12f, 0.52f, Color(0xFFBDE0FE), 8f, 0f, 15f, 0.9f, ParticleType.DOT, 0.6f),
        ConfettiParticle(0.88f, 0.50f, Color(0xFFFF758F), 13f, -15f, -25f, 0.8f, ParticleType.HEART, 0.0f),
        ConfettiParticle(0.22f, 0.44f, Color(0xFFFFD166), 10f, 0f, 15f, 0.6f, ParticleType.STAR, 0.5f),
        ConfettiParticle(0.78f, 0.42f, Color(0xFFD8BBFF), 8f, 0f, 12f, 0.7f, ParticleType.DOT, 0.3f),
        ConfettiParticle(0.10f, 0.65f, Color(0xFFFF8FA3), 14f, 20f, 20f, 1.0f, ParticleType.HEART, 0.2f),
        ConfettiParticle(0.90f, 0.62f, Color(0xFFA2D2FF), 9f, 0f, 15f, 0.9f, ParticleType.DOT, 0.7f),
        ConfettiParticle(0.05f, 0.78f, Color(0xFFC77DFF), 12f, 35f, 25f, 0.8f, ParticleType.STAR, 0.4f),
        ConfettiParticle(0.95f, 0.76f, Color(0xFFFFB4D9), 15f, -20f, -30f, 1.1f, ParticleType.HEART, 0.8f),
        ConfettiParticle(0.18f, 0.86f, Color(0xFFBDE0FE), 8f, 0f, 10f, 0.5f, ParticleType.DOT, 0.1f),
        ConfettiParticle(0.82f, 0.84f, Color(0xFFFFD166), 10f, 0f, 12f, 0.6f, ParticleType.STAR, 0.9f),
        ConfettiParticle(0.50f, 0.04f, Color(0xFFFF758F), 13f, 10f, -15f, 0.7f, ParticleType.HEART, 0.4f),
        ConfettiParticle(0.30f, 0.08f, Color(0xFFD8BBFF), 7f, 0f, 10f, 0.5f, ParticleType.DOT, 0.6f),
        ConfettiParticle(0.70f, 0.07f, Color(0xFFFFCCD5), 11f, -10f, 15f, 0.5f, ParticleType.HEART, 0.2f),
    )

/**
 * 预设的表白日浪漫阳光沙滩粒子集合（心形、阳光星芒、贝壳彩带与金色微光点）。
 */
private val PresetConfessionParticles: List<ConfettiParticle> =
    listOf(
        ConfettiParticle(0.12f, 0.10f, Color(0xFFFF758F), 14f, 15f, 25f, 1.0f, ParticleType.HEART, 0.1f),
        ConfettiParticle(0.88f, 0.08f, Color(0xFFF4A261), 15f, -20f, -30f, 1.2f, ParticleType.STAR, 0.5f),
        ConfettiParticle(0.06f, 0.22f, Color(0xFFFFB4A2), 11f, 45f, 40f, 0.8f, ParticleType.CONFETTI_RECT, 0.9f),
        ConfettiParticle(0.94f, 0.20f, Color(0xFFFF8FA3), 13f, -30f, -35f, 1.1f, ParticleType.HEART, 0.3f),
        ConfettiParticle(0.18f, 0.04f, Color(0xFFFFD166), 10f, 0f, 15f, 0.7f, ParticleType.STAR, 0.7f),
        ConfettiParticle(0.82f, 0.04f, Color(0xFFA2D2FF), 8f, 0f, 12f, 0.9f, ParticleType.DOT, 0.2f),
        ConfettiParticle(0.05f, 0.36f, Color(0xFFFFD166), 13f, 25f, -25f, 1.0f, ParticleType.STAR, 0.8f),
        ConfettiParticle(0.95f, 0.34f, Color(0xFFFF758F), 15f, -40f, 30f, 1.3f, ParticleType.HEART, 0.4f),
        ConfettiParticle(0.14f, 0.46f, Color(0xFFE2C2FF), 10f, 50f, 35f, 0.9f, ParticleType.CONFETTI_RECT, 0.6f),
        ConfettiParticle(0.86f, 0.44f, Color(0xFFF4A261), 12f, -15f, -20f, 0.8f, ParticleType.STAR, 0.0f),
        ConfettiParticle(0.25f, 0.40f, Color(0xFFFFB4A2), 7f, 0f, 10f, 0.6f, ParticleType.DOT, 0.5f),
        ConfettiParticle(0.75f, 0.38f, Color(0xFFFF8FA3), 8f, 0f, 12f, 0.7f, ParticleType.DOT, 0.3f),
        ConfettiParticle(0.10f, 0.60f, Color(0xFFFF758F), 14f, 25f, 20f, 1.0f, ParticleType.HEART, 0.2f),
        ConfettiParticle(0.90f, 0.58f, Color(0xFFFFD166), 12f, -10f, -15f, 0.9f, ParticleType.STAR, 0.7f),
        ConfettiParticle(0.06f, 0.74f, Color(0xFFFFCCD5), 11f, 45f, 25f, 0.8f, ParticleType.CONFETTI_RECT, 0.4f),
        ConfettiParticle(0.94f, 0.72f, Color(0xFFFF8FA3), 14f, -45f, -30f, 1.1f, ParticleType.HEART, 0.8f),
        ConfettiParticle(0.20f, 0.82f, Color(0xFFF4A261), 7f, 0f, 10f, 0.5f, ParticleType.DOT, 0.1f),
        ConfettiParticle(0.80f, 0.80f, Color(0xFFFFD166), 8f, 0f, 10f, 0.6f, ParticleType.DOT, 0.9f),
        ConfettiParticle(0.50f, 0.03f, Color(0xFFFF758F), 12f, 15f, -15f, 0.7f, ParticleType.HEART, 0.4f),
        ConfettiParticle(0.32f, 0.06f, Color(0xFFA2D2FF), 6f, 0f, 10f, 0.5f, ParticleType.DOT, 0.6f),
        ConfettiParticle(0.68f, 0.06f, Color(0xFFFFB4A2), 6f, 0f, 10f, 0.5f, ParticleType.DOT, 0.2f),
    )

/**
 * 七夕问候遮罩：七夕当天（农历七月初七，闰七月不算）每次冷启动后全屏盖在主界面上。
 *
 * 壁纸（线条小狗雨天撑伞）铺满全屏，搭配浪漫心形与星芒粒子浮动动效、半透明玫瑰奶白质感卡片、
 * 盛开玫瑰与「七夕快乐！」艺术字（站酷快乐体子集，OFL），以及底部轻触提示。
 * 入场采用级联弹跳与浮入动效，点击任意处或按返回键以 300ms 淡出退出。非七夕日期不渲染任何内容。
 * 可见状态随进程存续：旋转等配置变更不会重新弹出，冷启动重新判定。
 *
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun QixiGreetingOverlay(modifier: Modifier = Modifier) {
    val entranceProgress = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.88f) }
    val roseProgress = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }
    val subtitleProgress = remember { Animatable(0f) }
    val hintAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "QixiAtmosphere")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "QixiShimmerPhase",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "QixiPulseAlpha",
    )

    LaunchedEffect(Unit) {
        launch {
            entranceProgress.animateTo(1f, tween(800, easing = EmphasizedDecelerateEasing))
        }
        launch {
            cardScale.animateTo(1f, tween(500, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(100)
            roseProgress.animateTo(1f, tween(450, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(220)
            textProgress.animateTo(1f, tween(460, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(360)
            subtitleProgress.animateTo(1f, tween(420, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(600)
            hintAlpha.animateTo(1f, tween(400, easing = LinearEasing))
        }
    }

    GreetingOverlayShell(
        active = QixiChecker().isQixiToday(),
        wallpaper = CoreR.drawable.qixi_wallpaper,
        topPadding = 44.dp,
        modifier = modifier,
        backgroundOverlay = {
            ConfettiSparklesCanvas(
                particles = PresetQixiParticles,
                entranceProgress = entranceProgress.value,
                shimmerPhase = shimmerPhase,
            )
        },
        bottomContent = {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xE6FFFFFF),
                border = BorderStroke(1.dp, Color(0x60FFD6E0)),
                shadowElevation = 4.dp,
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
                    .graphicsLayer {
                        alpha = hintAlpha.value * pulseAlpha
                    },
            ) {
                Text(
                    text = "✨ 轻触屏幕开启美好一天 ✨",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF7A6870),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xF7FFFDFE),
            border =
            BorderStroke(
                width = 1.5.dp,
                brush =
                Brush.linearGradient(
                    listOf(
                        Color(0xCCFFD6E0),
                        Color(0x99CDB4DB),
                        Color(0xCCFFD6E0),
                    ),
                ),
            ),
            modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0x33E07A5F),
                    spotColor = Color(0x33CDB4DB),
                )
                .graphicsLayer {
                    alpha = entranceProgress.value
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    translationY = (1f - entranceProgress.value) * -20.dp.toPx()
                },
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部微标：07.07 · QIXI FESTIVAL
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF0F3),
                    border = BorderStroke(1.dp, Color(0xFFFFCCD5)),
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = roseProgress.value
                        translationY = (1f - roseProgress.value) * -8.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✨ 07.07 · QIXI FESTIVAL ✨",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD81E5B),
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.5.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 盛开玫瑰与两翼粉金星芒
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = roseProgress.value
                        val bounceScale = 0.7f + 0.3f * roseProgress.value
                        scaleX = bounceScale
                        scaleY = bounceScale
                        translationY = (roseProgress.value - 1f) * 16.dp.toPx() + sin(shimmerPhase) * 2.5.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✦",
                        color = Color(0xFFFF758F),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_rose),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "✦",
                        color = Color(0xFFFF758F),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 艺术字「七夕快乐！」（站酷快乐体）
                Text(
                    text = "七夕快乐！",
                    color = GreetingTextColor,
                    fontFamily = GreetingFontFamily,
                    fontSize = 54.sp,
                    textAlign = TextAlign.Center,
                    style =
                    TextStyle(
                        shadow =
                        Shadow(
                            color = Color(0x38E5383B),
                            offset = Offset(0f, 3.5f),
                            blurRadius = 7f,
                        ),
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = textProgress.value
                            val textScale = 0.8f + 0.2f * textProgress.value
                            scaleX = textScale
                            scaleY = textScale
                            translationY = (1f - textProgress.value) * 16.dp.toPx()
                        },
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 浪漫祝福胶囊标签：浪漫不止七夕 · 有你便是晴天
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF0F3),
                    border = BorderStroke(1.dp, Color(0xFFFFCCD5)),
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = subtitleProgress.value
                        val subScale = 0.85f + 0.15f * subtitleProgress.value
                        scaleX = subScale
                        scaleY = subScale
                        translationY = (1f - subtitleProgress.value) * 12.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✨ 浪漫不止七夕 · 有你便是晴天 ✨",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8B3A4A),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp),
                    )
                }
            }
        }
    }
}

/**
 * 生日问候遮罩：每年公历 9 月 4 日每次冷启动后全屏盖在主界面上。
 *
 * 壁纸（线条小狗与钞票雨）铺满全屏，搭配粒子星芒彩带浮动动效、半透明奶油质感卡片、
 * 金色生日皇冠与「生日快乐！」艺术字（站酷快乐体子集，OFL），以及底部轻触提示。
 * 入场采用级联弹跳与浮入动效，点击任意处或按返回键以 300ms 淡出退出。非生日日期不渲染任何内容。
 * 可见状态随进程存续：旋转等配置变更不会重新弹出，冷启动重新判定。
 *
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun BirthdayGreetingOverlay(modifier: Modifier = Modifier) {
    val entranceProgress = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.88f) }
    val crownProgress = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }
    val subtitleProgress = remember { Animatable(0f) }
    val hintAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "BirthdayAtmosphere")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ShimmerPhase",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "PulseAlpha",
    )

    LaunchedEffect(Unit) {
        launch {
            entranceProgress.animateTo(1f, tween(800, easing = EmphasizedDecelerateEasing))
        }
        launch {
            cardScale.animateTo(1f, tween(500, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(100)
            crownProgress.animateTo(1f, tween(450, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(220)
            textProgress.animateTo(1f, tween(460, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(360)
            subtitleProgress.animateTo(1f, tween(420, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(600)
            hintAlpha.animateTo(1f, tween(400, easing = LinearEasing))
        }
    }

    GreetingOverlayShell(
        active = BirthdayChecker().isBirthdayToday(),
        wallpaper = CoreR.drawable.birthday_wallpaper,
        topPadding = 44.dp,
        modifier = modifier,
        backgroundOverlay = {
            ConfettiSparklesCanvas(
                particles = PresetBirthdayParticles,
                entranceProgress = entranceProgress.value,
                shimmerPhase = shimmerPhase,
            )
        },
        bottomContent = {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xE6FFFFFF),
                border = BorderStroke(1.dp, Color(0x60FFD6BA)),
                shadowElevation = 4.dp,
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
                    .graphicsLayer {
                        alpha = hintAlpha.value * pulseAlpha
                    },
            ) {
                Text(
                    text = "✨ 轻触屏幕开启美好一天 ✨",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF7A685F),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xF7FFFDF9),
            border =
            BorderStroke(
                width = 1.5.dp,
                brush =
                Brush.linearGradient(
                    listOf(
                        Color(0xCCFFE0B8),
                        Color(0x99FFAAA0),
                        Color(0xCCFFE0B8),
                    ),
                ),
            ),
            modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0x33E26D5C),
                    spotColor = Color(0x33E26D5C),
                )
                .graphicsLayer {
                    alpha = entranceProgress.value
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    translationY = (1f - entranceProgress.value) * -20.dp.toPx()
                },
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部微标：09.04 · HAPPY BIRTHDAY
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEFEA),
                    border = BorderStroke(1.dp, Color(0xFFFFD4C8)),
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = crownProgress.value
                        translationY = (1f - crownProgress.value) * -8.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✨ 09.04 · HAPPY BIRTHDAY ✨",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD63840),
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.5.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 金色生日皇冠与两翼小星芒
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = crownProgress.value
                        val bounceScale = 0.7f + 0.3f * crownProgress.value
                        scaleX = bounceScale
                        scaleY = bounceScale
                        translationY = (crownProgress.value - 1f) * 16.dp.toPx() + sin(shimmerPhase) * 2.5.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✦",
                        color = Color(0xFFFFB703),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_birthday_crown),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(56.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "✦",
                        color = Color(0xFFFFB703),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 艺术字「生日快乐！」（站酷快乐体）
                Text(
                    text = "生日快乐！",
                    color = GreetingTextColor,
                    fontFamily = GreetingFontFamily,
                    fontSize = 54.sp,
                    textAlign = TextAlign.Center,
                    style =
                    TextStyle(
                        shadow =
                        Shadow(
                            color = Color(0x38E5383B),
                            offset = Offset(0f, 3.5f),
                            blurRadius = 7f,
                        ),
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = textProgress.value
                            val textScale = 0.8f + 0.2f * textProgress.value
                            scaleX = textScale
                            scaleY = textScale
                            translationY = (1f - textProgress.value) * 16.dp.toPx()
                        },
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 祝福胶囊标签：暴富暴美 · 天天开心
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF0EA),
                    border = BorderStroke(1.dp, Color(0xFFFFDBD0)),
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = subtitleProgress.value
                        val subScale = 0.85f + 0.15f * subtitleProgress.value
                        scaleX = subScale
                        scaleY = subScale
                        translationY = (1f - subtitleProgress.value) * 12.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✨ 暴富暴美 · 天天开心 ✨",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF7C3E2E),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp),
                    )
                }
            }
        }
    }
}

/**
 * 表白日问候遮罩：每年公历 11 月 4 日每次冷启动后全屏盖在主界面上。
 *
 * 壁纸（线条小狗阳光沙滩爱心框）铺满全屏，搭配浪漫心形与阳光金星粒子浮动动效、
 * 半透明温润暖白质感卡片、立体爱心与「我喜欢你！」艺术字（站酷快乐体子集，OFL），以及底部轻触提示。
 * 入场采用级联弹跳与浮入动效，点击任意处或按返回键以 300ms 淡出退出。非表白日不渲染任何内容。
 * 可见状态随进程存续：旋转等配置变更不会重新弹出，冷启动重新判定。
 *
 * @param modifier 外部传入的 Modifier
 */
@Composable
fun ConfessionGreetingOverlay(modifier: Modifier = Modifier) {
    val entranceProgress = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.88f) }
    val heartProgress = remember { Animatable(0f) }
    val textProgress = remember { Animatable(0f) }
    val subtitleProgress = remember { Animatable(0f) }
    val hintAlpha = remember { Animatable(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "ConfessionAtmosphere")
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ConfessionShimmerPhase",
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ConfessionPulseAlpha",
    )

    LaunchedEffect(Unit) {
        launch {
            entranceProgress.animateTo(1f, tween(800, easing = EmphasizedDecelerateEasing))
        }
        launch {
            cardScale.animateTo(1f, tween(500, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(100)
            heartProgress.animateTo(1f, tween(450, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(220)
            textProgress.animateTo(1f, tween(460, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(360)
            subtitleProgress.animateTo(1f, tween(420, easing = EmphasizedDecelerateEasing))
        }
        launch {
            delay(600)
            hintAlpha.animateTo(1f, tween(400, easing = LinearEasing))
        }
    }

    GreetingOverlayShell(
        active = ConfessionChecker().isConfessionToday(),
        wallpaper = CoreR.drawable.confession_wallpaper,
        topPadding = 44.dp,
        modifier = modifier,
        backgroundOverlay = {
            ConfettiSparklesCanvas(
                particles = PresetConfessionParticles,
                entranceProgress = entranceProgress.value,
                shimmerPhase = shimmerPhase,
            )
        },
        bottomContent = {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color(0xE6FFFFFF),
                border = BorderStroke(1.dp, Color(0x60FFD6BA)),
                shadowElevation = 4.dp,
                modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp)
                    .graphicsLayer {
                        alpha = hintAlpha.value * pulseAlpha
                    },
            ) {
                Text(
                    text = "✨ 轻触屏幕开启心动一天 ✨",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF7A685F),
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xF7FFFDF8),
            border =
            BorderStroke(
                width = 1.5.dp,
                brush =
                Brush.linearGradient(
                    listOf(
                        Color(0xCCFFD6A5),
                        Color(0x99FFAAA0),
                        Color(0xCCFFD6A5),
                    ),
                ),
            ),
            modifier =
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(32.dp),
                    ambientColor = Color(0x33E07A5F),
                    spotColor = Color(0x33F4A261),
                )
                .graphicsLayer {
                    alpha = entranceProgress.value
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    translationY = (1f - entranceProgress.value) * -20.dp.toPx()
                },
        ) {
            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 顶部微标：11.04 · CONFESSION DAY
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF0E6),
                    border = BorderStroke(1.dp, Color(0xFFFFD8C4)),
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = heartProgress.value
                        translationY = (1f - heartProgress.value) * -8.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✨ 11.04 · CONFESSION DAY ✨",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE76F51),
                        letterSpacing = 1.4.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.5.dp),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 立体爱心与两翼暖金星芒
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = heartProgress.value
                        val bounceScale = 0.7f + 0.3f * heartProgress.value
                        scaleX = bounceScale
                        scaleY = bounceScale
                        translationY = (heartProgress.value - 1f) * 16.dp.toPx() + sin(shimmerPhase) * 2.5.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✦",
                        color = Color(0xFFF4A261),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_heart),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "✦",
                        color = Color(0xFFF4A261),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 艺术字「我喜欢你！」（站酷快乐体）
                Text(
                    text = "我喜欢你！",
                    color = GreetingTextColor,
                    fontFamily = GreetingFontFamily,
                    fontSize = 54.sp,
                    textAlign = TextAlign.Center,
                    style =
                    TextStyle(
                        shadow =
                        Shadow(
                            color = Color(0x38E5383B),
                            offset = Offset(0f, 3.5f),
                            blurRadius = 7f,
                        ),
                    ),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = textProgress.value
                            val textScale = 0.8f + 0.2f * textProgress.value
                            scaleX = textScale
                            scaleY = textScale
                            translationY = (1f - textProgress.value) * 16.dp.toPx()
                        },
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 浪漫祝福胶囊标签：阳光、海浪，还有最爱的你
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFFFF2EB),
                    border = BorderStroke(1.dp, Color(0xFFFFD9C7)),
                    modifier =
                    Modifier.graphicsLayer {
                        alpha = subtitleProgress.value
                        val subScale = 0.85f + 0.15f * subtitleProgress.value
                        scaleX = subScale
                        scaleY = subScale
                        translationY = (1f - subtitleProgress.value) * 12.dp.toPx()
                    },
                ) {
                    Text(
                        text = "✨ 阳光、海浪，还有最爱的你 ✨",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF8C4A32),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.5.dp),
                    )
                }
            }
        }
    }
}

/**
 * 绘制庆祝粒子（星芒、彩纸碎屑与柔光圆点）。
 */
@Composable
private fun ConfettiSparklesCanvas(
    particles: List<ConfettiParticle>,
    entranceProgress: Float,
    shimmerPhase: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        for (particle in particles) {
            val wobbleX = sin(shimmerPhase * particle.floatSpeed + particle.phase) * 8.dp.toPx()
            val wobbleY = (1f - entranceProgress) * 40.dp.toPx() + sin(shimmerPhase * 0.7f + particle.phase) * 4.dp.toPx()

            val px = particle.initialXRatio * canvasWidth + wobbleX
            val py = particle.initialYRatio * canvasHeight + wobbleY
            val pAlpha = (entranceProgress * (0.65f + 0.35f * sin(shimmerPhase + particle.phase))).coerceIn(0f, 1f)
            val pColor = particle.color.copy(alpha = particle.color.alpha * pAlpha)

            when (particle.type) {
                ParticleType.STAR -> {
                    val radius = particle.sizeDp.dp.toPx() * (0.8f + 0.2f * entranceProgress)
                    drawStar(
                        center = Offset(px, py),
                        outerRadius = radius,
                        innerRadius = radius * 0.28f,
                        color = pColor,
                        rotationDeg = particle.rotationDeg + shimmerPhase * particle.rotationSpeed,
                    )
                }

                ParticleType.CONFETTI_RECT -> {
                    val w = particle.sizeDp.dp.toPx() * (0.8f + 0.2f * entranceProgress)
                    val h = w * 0.5f
                    drawConfettiRect(
                        center = Offset(px, py),
                        width = w,
                        height = h,
                        color = pColor,
                        rotationDeg = particle.rotationDeg + shimmerPhase * particle.rotationSpeed,
                    )
                }

                ParticleType.DOT -> {
                    val r = particle.sizeDp.dp.toPx() * 0.5f * (0.8f + 0.2f * entranceProgress)
                    drawCircle(
                        color = pColor,
                        radius = r,
                        center = Offset(px, py),
                    )
                }

                ParticleType.HEART -> {
                    val s = particle.sizeDp.dp.toPx() * (0.8f + 0.2f * entranceProgress)
                    drawHeart(
                        center = Offset(px, py),
                        size = s,
                        color = pColor,
                        rotationDeg = particle.rotationDeg + shimmerPhase * particle.rotationSpeed,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawHeart(
    center: Offset,
    size: Float,
    color: Color,
    rotationDeg: Float = 0f,
) {
    rotate(rotationDeg, pivot = center) {
        val path =
            Path().apply {
                val width = size
                val height = size
                val left = center.x - width / 2f
                val top = center.y - height / 2f

                moveTo(center.x, top + height * 0.28f)
                cubicTo(
                    center.x - width * 0.15f,
                    top,
                    left,
                    top + height * 0.08f,
                    left,
                    top + height * 0.38f,
                )
                cubicTo(
                    left,
                    top + height * 0.65f,
                    center.x - width * 0.2f,
                    top + height * 0.82f,
                    center.x,
                    top + height,
                )
                cubicTo(
                    center.x + width * 0.2f,
                    top + height * 0.82f,
                    left + width,
                    top + height * 0.65f,
                    left + width,
                    top + height * 0.38f,
                )
                cubicTo(
                    left + width,
                    top + height * 0.08f,
                    center.x + width * 0.15f,
                    top,
                    center.x,
                    top + height * 0.28f,
                )
                close()
            }
        drawPath(path, color)
    }
}

private fun DrawScope.drawStar(
    center: Offset,
    outerRadius: Float,
    innerRadius: Float = outerRadius * 0.28f,
    color: Color,
    rotationDeg: Float = 0f,
) {
    rotate(rotationDeg, pivot = center) {
        val path =
            Path().apply {
                moveTo(center.x, center.y - outerRadius)
                lineTo(center.x + innerRadius, center.y - innerRadius)
                lineTo(center.x + outerRadius, center.y)
                lineTo(center.x + innerRadius, center.y + innerRadius)
                lineTo(center.x, center.y + outerRadius)
                lineTo(center.x - innerRadius, center.y + innerRadius)
                lineTo(center.x - outerRadius, center.y)
                lineTo(center.x - innerRadius, center.y - innerRadius)
                close()
            }
        drawPath(path, color)
    }
}

private fun DrawScope.drawConfettiRect(
    center: Offset,
    width: Float,
    height: Float,
    color: Color,
    rotationDeg: Float,
) {
    rotate(rotationDeg, pivot = center) {
        drawRoundRect(
            color = color,
            topLeft = Offset(center.x - width / 2f, center.y - height / 2f),
            size = Size(width, height),
            cornerRadius = CornerRadius(width / 4f, width / 4f),
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
 * @param backgroundOverlay 背景层与内容之间的全屏装饰层（如彩带星芒粒子）
 * @param bottomContent 底部悬浮操作或提示内容
 * @param content 顶部居中的问候内容（艺术字等）
 */
@Composable
private fun GreetingOverlayShell(
    active: Boolean,
    wallpaper: Int,
    topPadding: Dp = 64.dp,
    modifier: Modifier = Modifier,
    backgroundOverlay: @Composable (BoxScope.() -> Unit)? = null,
    bottomContent: @Composable (BoxScope.() -> Unit)? = null,
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

            backgroundOverlay?.invoke(this)

            Column(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = topPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }

            bottomContent?.invoke(this)
        }
    }
}
