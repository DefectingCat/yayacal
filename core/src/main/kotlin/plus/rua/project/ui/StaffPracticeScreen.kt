@file:OptIn(ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import plus.rua.project.StaffGeometry
import plus.rua.project.StaffNote
import plus.rua.project.StaffQuiz
import plus.rua.project.StaffQuiz.Direction
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

// M3 emphasized 缓动（本仓 compose 版本无内置常量，按 token 手写贝塞尔）
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

// 作答反馈色（深绿在深浅主题下均可读）
private val CorrectContainer = Color(0xFF2E7D32)
private val OnCorrectContainer = Color(0xFFFFFFFF)

private const val QUIZ_TAB_INDEX = 1

/**
 * 五线谱练习页面：认识钢琴高音谱表的教学 + 双向识谱练习。
 *
 * 「教学」：五线谱基础讲解、线/间结构图解、音名/唱名/简谱对照、
 * 可点按的 do~si 音阶阶梯与钢琴键盘（点按联动查看音名/唱名/谱面位置）、
 * 高音低音教学（三个八度键盘与五线谱联动、简谱高低音点、大谱表与中央 C 桥梁）、
 * 完整五线谱音位图（C4~C6 十五个自然音逐一标记，点按查看名称与位置），
 * 以及逐步引导的「互动小课堂」：点音符作答，答错报音名并给位置提示，答对自动进阶。
 * 「练习」：两种方向 —— 看谱认唱名（上方音符、下方选唱名）、
 * 听名找位置（上方唱名、下方选五线谱上的音符），答错会标出正确答案。
 * 每个交互都有克制的过渡动画：Tab/题目滑动切换、选项逐个弹入、
 * 答对轻微弹跳、答错小幅抖动、连击计数弹跳。
 *
 * @param onBack 返回回调
 * @param modifier 布局修饰符
 */
@Composable
fun StaffPracticeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "认识五线谱",
                        style =
                        MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "返回",
                        )
                    }
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = tabIndex == 0,
                    onClick = { tabIndex = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.School,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text("教学")
                }
                SegmentedButton(
                    selected = tabIndex == QUIZ_TAB_INDEX,
                    onClick = { tabIndex = QUIZ_TAB_INDEX },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                ) {
                    Text("练习")
                }
            }

            // Tab 切换：按方向滑动 + 淡入淡出
            AnimatedContent(
                targetState = tabIndex,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (
                        slideInHorizontally(
                            tween(320, easing = EmphasizedDecelerate),
                        ) { direction * it / 3 } + fadeIn(tween(260))
                        ) togetherWith
                        (
                            slideOutHorizontally(
                                tween(220, easing = EmphasizedAccelerate),
                            ) { -direction * it / 3 } + fadeOut(tween(180))
                            )
                },
                label = "staffTab",
                modifier = Modifier.fillMaxSize(),
            ) { tab ->
                when (tab) {
                    0 ->
                        TeachingTab(
                            onGoQuiz = { tabIndex = QUIZ_TAB_INDEX },
                            modifier = Modifier.fillMaxSize(),
                        )

                    else -> QuizTab(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// region 教学

@Composable
private fun TeachingTab(
    onGoQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 一个八度的音阶阶梯：C4(do) ~ C5(do)
    val teachingNotes = remember { (0..7).map(::StaffNote) }
    var selected by remember { mutableStateOf(StaffNote(0)) }
    var explorerNote by remember { mutableStateOf(StaffNote(7)) }
    var chartNote by remember { mutableStateOf(StaffNote(0)) }

    Column(
        modifier =
        modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LessonCard(title = "五线谱是什么") {
            Text(
                "五线谱由五条平行的横线组成，自下而上称为第一线到第五线，线与线之间叫「间」。" +
                    "音越高，音符在五线谱上的位置就越高；超出五线的音用「加线」表示。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "一个音符由符头和符干组成（本页用的是四分音符）。符干朝向有规律：" +
                    "第三线以下的音符符干朝上，第三线及以上朝下。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LessonCard(title = "五线谱的结构") {
            StaffStructureDiagram(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(160.dp),
            )
        }

        LessonCard(title = "音名、唱名与简谱") {
            NoteNameTable()
            Text(
                "固定 do 唱名法里，do 永远对应音名 C（简谱 1）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LessonCard(title = "互动小课堂") {
            GuidedLesson(onGoQuiz = onGoQuiz)
        }

        LessonCard(title = "点一点，认识 do ~ si") {
            StaffCanvas(
                notes = teachingNotes,
                selectedNote = selected,
                labelFor = { it.solfege.label },
                onNoteClick = { selected = it },
                animateEntrance = true,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(200.dp),
            )
            // 选中音符详情：交叉淡入 + 上移
            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (
                        fadeIn(tween(200)) +
                            slideInVertically(
                                tween(260, easing = EmphasizedDecelerate),
                            ) { it / 4 }
                        ) togetherWith fadeOut(tween(140))
                },
                label = "noteDetail",
            ) { note ->
                NoteDetailCard(note)
            }
        }

        LessonCard(title = "在钢琴上找到它们") {
            PianoKeyboard(
                selected = selected,
                onSelect = { selected = it },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(130.dp),
            )
            Text(
                "白键从左到右就是 do ~ si（C D E F G A B），带圆点的是中央 C。" +
                    "点一点白键，和上面的五线谱对照着看。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LessonCard(title = "高音与低音") {
            OctaveExplorer(
                selected = explorerNote,
                onSelect = { explorerNote = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LessonCard(title = "完整的五线谱音位图") {
            FullStaffChart(
                selected = chartNote,
                onSelect = { chartNote = it },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(260.dp),
            )
            NoteDetailCard(chartNote)
            Text(
                "从中音 do 到高音 do，十五个自然音从低到高排在谱上；高八度的唱名带一个上加点。点一点音符，看它的名称与位置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LessonCard(title = "高音与低音：大谱表") {
            GrandStaffDiagram(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
            Text(
                "钢琴谱把两行五线谱合在一起用：上面是高音谱表，通常右手弹；下面是低音谱表，通常左手弹。" +
                    "高音谱号的螺旋绕在第二线，所以也叫 G 谱号；低音谱号两个点夹着第四线，所以也叫 F 谱号。" +
                    "中央 C 正好夹在两行谱中间的加线上，是钢琴上同一个键。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LessonCard(title = "位置口诀") {
            MnemonicRow("线上音（一线 → 五线）", "mi、sol、si、re、fa")
            MnemonicRow("间上音（一间 → 四间）", "fa、la、do、mi")
            MnemonicRow("下加一线", "do，就是钢琴上的中央 C")
            MnemonicRow("数字定位法", "从下加一线开始数 1（do），像爬楼梯一样一间一线往上数：2、3、4、5、6、7")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LessonCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun MnemonicRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 五线谱结构图解：左侧标注五条线（第五线 → 第一线），右侧标注四个间（第四间 → 第一间）。
 */
@Composable
private fun StaffStructureDiagram(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val lineLabelColor = MaterialTheme.colorScheme.primary
    val spaceLabelColor = MaterialTheme.colorScheme.tertiary
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )

    Canvas(modifier = modifier) {
        val spacing = size.height / 6.4f
        val topLineY = spacing * 1.2f
        val staffLeft = spacing * 3.6f
        val staffRight = size.width - spacing * 3.6f

        // 五条线 + 左侧线名
        for (line in 0..4) {
            val y = topLineY + spacing * line
            drawLine(
                color = lineColor,
                start = Offset(staffLeft, y),
                end = Offset(staffRight, y),
                strokeWidth = spacing * 0.09f,
            )
            val label =
                textMeasurer.measure(
                    "第${cnNumeral(5 - line)}线",
                    style = labelStyle.copy(color = lineLabelColor),
                )
            drawText(
                textLayoutResult = label,
                topLeft =
                Offset(
                    staffLeft - label.size.width - spacing * 0.3f,
                    y - label.size.height / 2f,
                ),
            )
        }

        // 四个间 + 右侧间名
        for (space in 0..3) {
            val y = topLineY + spacing * (space + 0.5f)
            val label =
                textMeasurer.measure(
                    "第${cnNumeral(4 - space)}间",
                    style = labelStyle.copy(color = spaceLabelColor),
                )
            drawText(
                textLayoutResult = label,
                topLeft =
                Offset(
                    staffRight + spacing * 0.3f,
                    y - label.size.height / 2f,
                ),
            )
        }
    }
}

/**
 * 音名 / 唱名 / 简谱三行对照表（C 大调固定 do）。
 */
@Composable
private fun NoteNameTable() {
    val rows =
        listOf(
            Triple("音名", listOf("C", "D", "E", "F", "G", "A", "B"), false),
            Triple("唱名", listOf("do", "re", "mi", "fa", "sol", "la", "si"), true),
            Triple("简谱", listOf("1", "2", "3", "4", "5", "6", "7"), false),
        )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, cells, serif) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(44.dp),
                )
                cells.forEach { cell ->
                    Text(
                        text = cell,
                        style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = if (serif) FontFamily.Serif else null,
                            fontStyle = if (serif) FontStyle.Italic else null,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * 一个八度的钢琴键盘（C4 ~ C5）：白键可点按，与五线谱阶梯联动选中。
 *
 * 黑键仅作装饰（本页只教自然音）；中央 C 键带底色与圆点标记。
 *
 * @param selected 当前选中音符（决定哪个白键高亮）
 * @param onSelect 点按白键回调，参数为对应音符
 * @param modifier 布局修饰符
 */
@Composable
private fun PianoKeyboard(
    selected: StaffNote,
    onSelect: (StaffNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val whiteColor = MaterialTheme.colorScheme.surface
    val middleCColor = MaterialTheme.colorScheme.primaryContainer
    val selectedFillColor = MaterialTheme.colorScheme.tertiaryContainer
    val selectedBorderColor = MaterialTheme.colorScheme.tertiary
    val selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
    val blackKeyColor = MaterialTheme.colorScheme.onSurface
    val markerColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )

    Canvas(
        modifier =
        modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val whiteWidth = size.width / 8f
                val index = (tap.x / whiteWidth).toInt().coerceIn(0, 7)
                onSelect(StaffNote(index))
            }
        },
    ) {
        val whiteWidth = size.width / 8f
        val labelBand = size.height * 0.22f
        val keyHeight = size.height - labelBand
        val corner = CornerRadius(whiteWidth * 0.12f, whiteWidth * 0.12f)

        // 白键
        for (index in 0..7) {
            val x = index * whiteWidth
            val isSelected = selected.step == index
            val fill =
                when {
                    isSelected -> selectedFillColor
                    index == 0 -> middleCColor
                    else -> whiteColor
                }
            drawRoundRect(
                color = fill,
                topLeft = Offset(x + 1f, 0f),
                size = Size(whiteWidth - 2f, keyHeight),
                cornerRadius = corner,
            )
            drawRoundRect(
                color = if (isSelected) selectedBorderColor else outlineColor,
                topLeft = Offset(x + 1f, 0f),
                size = Size(whiteWidth - 2f, keyHeight),
                cornerRadius = corner,
                style = Stroke(width = size.height * 0.012f),
            )
            // 中央 C 圆点标记
            if (index == 0 && !isSelected) {
                drawCircle(
                    color = markerColor,
                    radius = whiteWidth * 0.07f,
                    center = Offset(x + whiteWidth / 2f, keyHeight * 0.88f),
                )
            }
            // 唱名标签
            val label =
                textMeasurer.measure(
                    StaffNote(index).solfege.label,
                    style =
                    labelStyle.copy(
                        color = if (isSelected) selectedLabelColor else labelColor,
                    ),
                )
            drawText(
                textLayoutResult = label,
                topLeft =
                Offset(
                    x + whiteWidth / 2f - label.size.width / 2f,
                    keyHeight + (labelBand - label.size.height) / 2f,
                ),
            )
        }

        // 黑键（装饰，覆盖在白键缝隙上）：C# D# F# G# A#
        val blackWidth = whiteWidth * 0.58f
        val blackHeight = keyHeight * 0.58f
        listOf(0, 1, 3, 4, 5).forEach { afterWhite ->
            val centerX = (afterWhite + 1) * whiteWidth
            drawRoundRect(
                color = blackKeyColor,
                topLeft = Offset(centerX - blackWidth / 2f, 0f),
                size = Size(blackWidth, blackHeight),
                cornerRadius = CornerRadius(blackWidth * 0.18f, blackWidth * 0.18f),
            )
        }
    }
}

@Composable
private fun NoteDetailCard(note: StaffNote) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = note.solfege.label,
                style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text =
                    "${note.pitchName}${note.octave}" +
                        if (note.step == 0) " · 中央 C" else " · ${octaveName(note.octave)}音区",
                    style =
                    MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = positionText(note.step),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            JianpuText(
                note = note,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** 中文数字（谱面位置描述用，仅覆盖一到五，超出回退为阿拉伯数字）。 */
private fun cnNumeral(n: Int): String = listOf("一", "二", "三", "四", "五").getOrElse(n - 1) { n.toString() }

/** 五线谱位置的中文描述（第一线 / 第三间 / 下加一线 / 上加二间…）。 */
private fun positionText(step: Int): String {
    val offset = StaffGeometry.halfUnitsFromBottomLine(step)
    return when {
        offset == -1 -> "下加一间"
        offset <= -2 && offset % 2 == 0 -> "下加${cnNumeral(-offset / 2)}线"
        offset <= -3 -> "下加${cnNumeral((-offset + 1) / 2)}间"
        offset in 0..8 && offset % 2 == 0 -> "第${cnNumeral(offset / 2 + 1)}线"
        offset in 1..7 -> "第${cnNumeral((offset + 1) / 2)}间"
        offset == 9 -> "上加一间"
        offset % 2 == 0 -> "上加${cnNumeral((offset - 8) / 2)}线"
        else -> "上加${cnNumeral((offset - 7) / 2)}间"
    }
}

// region 高音低音与完整音位图

/** 简谱八度名（低音 / 中音 / 高音 / 倍高音…），超出常见范围按与中音的距离回退。 */
private fun octaveName(octave: Int): String = when (octave) {
    2 -> "倍低"
    3 -> "低"
    4 -> "中"
    5 -> "高"
    6 -> "倍高"
    else -> if (octave > 6) "高${octave - 4}" else "低${4 - octave}"
}

/** 简谱八度点：高八度每度一个上加点、低八度每度一个下加点；中音为空。 */
private fun jianpuDots(note: StaffNote): Int = note.octave - 4

/**
 * 简谱记法展示：数字唱名 + 八度点（高点在上、低点在下），与简谱书写一致。
 *
 * @param note 要展示的音符
 * @param color 数字与圆点颜色
 * @param modifier 布局修饰符
 */
@Composable
private fun JianpuText(
    note: StaffNote,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val dots = jianpuDots(note)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(dots.coerceAtLeast(0)) {
            Text(
                "·",
                style = MaterialTheme.typography.labelLarge.copy(lineHeight = 10.sp),
                color = color,
            )
        }
        Text(
            note.solfege.number.toString(),
            style =
            MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = color,
        )
        repeat((-dots).coerceAtLeast(0)) {
            Text(
                "·",
                style = MaterialTheme.typography.labelLarge.copy(lineHeight = 10.sp),
                color = color,
            )
        }
    }
}

/** 键盘视窗起点：C3 ~ C5（step -7..7）为死区不滚动，选中音超出后刚好滑回视窗边缘。 */
private fun keyboardWindowStart(step: Float): Float = when {
    step > 7f -> step - 14f
    step < -7f -> step
    else -> -7f
}

/**
 * 高音低音探索器：五个八度（C2 ~ C6）的钢琴键盘 + 高音谱表 + 详情行三方联动。
 *
 * 点白键或左右箭头切换音符：键盘高亮、谱上光晕、详情行同步更新，
 * 直观展示「同一个唱名，高八度在谱上更高、简谱数字上方加点」。
 * 谱面始终刚好容纳五线与选中音（不预留多余空白）；选中音滑出
 * C3~C5 键盘视窗后键盘跟随滑动。两者由同一个动画步进驱动，切换平滑。
 *
 * @param selected 当前选中音符
 * @param onSelect 选中变化回调，参数为新选中的音符
 * @param modifier 布局修饰符
 */
@Composable
private fun OctaveExplorer(
    selected: StaffNote,
    onSelect: (StaffNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val whiteColor = MaterialTheme.colorScheme.surface
    val middleCColor = MaterialTheme.colorScheme.primaryContainer
    val selectedFillColor = MaterialTheme.colorScheme.tertiaryContainer
    val selectedBorderColor = MaterialTheme.colorScheme.tertiary
    val blackKeyColor = MaterialTheme.colorScheme.onSurface
    val markerColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val keyLabelStyle =
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
        )

    // 谱面与键盘由同一个动画步进驱动：谱面刚好容纳五线与选中音，
    // 键盘在选中音滑出 C3~C5 视窗后跟随滑动，切换八度平滑过渡
    val animatedStep by animateFloatAsState(
        targetValue = selected.step.toFloat(),
        animationSpec = tween(350, easing = EmphasizedDecelerate),
        label = "explorerStep",
    )
    val keyboardStart = keyboardWindowStart(animatedStep)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 滑动键盘：一屏 15 个白键（两个八度），跟随选中音在 C2 ~ C6 间滑动
        Canvas(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val whiteWidth = size.width / 15f
                        val index = (tap.x / whiteWidth).toInt()
                        // animatedStep 是稳定的 State 委托，手势块里读到的是最新值
                        val step =
                            (keyboardWindowStart(animatedStep).roundToInt() + index)
                                .coerceIn(-14, 14)
                        currentOnSelect(StaffNote(step))
                    }
                },
        ) {
            val whiteWidth = size.width / 15f
            val labelBand = size.height * 0.24f
            val keyHeight = size.height - labelBand
            val corner = CornerRadius(whiteWidth * 0.12f, whiteWidth * 0.12f)
            // 视窗随 animatedStep 连续滑动，首尾多画一键覆盖滑动中的渐入渐出
            val firstStep = floor(keyboardStart).toInt()

            for (step in firstStep..firstStep + 15) {
                val x = (step - keyboardStart) * whiteWidth
                val isSelected = selected.step == step
                val fill =
                    when {
                        isSelected -> selectedFillColor
                        step == 0 -> middleCColor
                        else -> whiteColor
                    }
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(x + 1f, 0f),
                    size = Size(whiteWidth - 2f, keyHeight),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = if (isSelected) selectedBorderColor else outlineColor,
                    topLeft = Offset(x + 1f, 0f),
                    size = Size(whiteWidth - 2f, keyHeight),
                    cornerRadius = corner,
                    style = Stroke(width = size.height * 0.012f),
                )
                // 中央 C 圆点标记
                if (step == 0 && !isSelected) {
                    drawCircle(
                        color = markerColor,
                        radius = whiteWidth * 0.09f,
                        center = Offset(x + whiteWidth / 2f, keyHeight * 0.86f),
                    )
                }
                // 只在每个八度的 do 上标唱名，突出八度分组
                if (step.mod(7) == 0) {
                    val label =
                        textMeasurer.measure(
                            StaffNote(step).solfege.label,
                            style = keyLabelStyle.copy(color = textColor),
                        )
                    drawText(
                        textLayoutResult = label,
                        topLeft =
                        Offset(
                            x + whiteWidth / 2f - label.size.width / 2f,
                            keyHeight + (labelBand - label.size.height) / 2f,
                        ),
                    )
                }
            }

            // 黑键（装饰）：每个八度 C# D# F# G# A#
            val blackWidth = whiteWidth * 0.58f
            val blackHeight = keyHeight * 0.58f
            for (step in firstStep..firstStep + 15) {
                val pos = step.mod(7)
                if (pos != 0 && pos != 1 && pos != 3 && pos != 4 && pos != 5) continue
                val centerX = (step - keyboardStart + 1) * whiteWidth
                drawRoundRect(
                    color = blackKeyColor,
                    topLeft = Offset(centerX - blackWidth / 2f, 0f),
                    size = Size(blackWidth, blackHeight),
                    cornerRadius = CornerRadius(blackWidth * 0.18f, blackWidth * 0.18f),
                )
            }
        }

        // 高音谱表联动：选中音带光晕，谱面刚好容纳五线与选中音
        StaffCanvas(
            notes = listOf(selected),
            fitNote = animatedStep,
            selectedNote = selected,
            modifier =
            Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        // 详情行：箭头切换 + 简谱/唱名/音名
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 箭头区间与键盘总跨度一致（C2 ~ C6）
            IconButton(
                onClick = { currentOnSelect(StaffNote((selected.step - 1).coerceAtLeast(-14))) },
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowLeft,
                    contentDescription = "更低的音",
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    JianpuText(
                        note = selected,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text =
                        "${selected.solfege.label} · ${selected.pitchName}${selected.octave}" +
                            if (selected.step == 0) "（中央 C）" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            IconButton(
                onClick = { currentOnSelect(StaffNote((selected.step + 1).coerceAtMost(14))) },
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = "更高的音",
                )
            }
        }

        Text(
            "同一个唱名可以落在不同的八度：往右一个八度就是「高八度」，" +
                "在五线谱上整体往上挪，简谱数字上方加一个点；往左则低八度，点加在下方。" +
                "点点键盘或箭头，看看同一个唱名在高处和低处长什么样。",
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
        )
    }
}

/**
 * 完整五线谱音位图：C4 ~ C6 十五个自然音按音高从低到高排在一张高音谱表上，
 * 每个音符带唱名标签，点按选中后下方详情行同步（详情行展示简谱八度点）。
 *
 * @param selected 当前选中音符（画光晕）
 * @param onSelect 点按音符回调，参数为被点中的音符
 * @param modifier 布局修饰符
 */
@Composable
private fun FullStaffChart(
    selected: StaffNote,
    onSelect: (StaffNote) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSelect by rememberUpdatedState(onSelect)
    StaffCanvas(
        notes = (0..14).map(::StaffNote),
        selectedNote = selected,
        labelFor = { it.solfege.label },
        onNoteClick = { currentOnSelect(it) },
        animateEntrance = true,
        modifier = modifier,
    )
}

/**
 * 大谱表图解：高音谱表 + 低音谱表 + 左侧连接竖线，中央 C 画在中间的共享加线上。
 *
 * 高音谱表带 G 谱号螺旋示意、低音谱表带 F 谱号双点示意；
 * 右侧标注两行谱的左右手分工。
 *
 * @param modifier 布局修饰符
 */
@Composable
private fun GrandStaffDiagram(modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val accentColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
        )

    Canvas(modifier = modifier) {
        val spacing = size.height / 12.5f
        val staffLeft = spacing * 1.2f
        val staffRight = size.width - spacing * 4.2f
        val trebleBottomY = spacing * 4.8f
        val bassTopY = spacing * 7.8f
        val middleCY = spacing * 6.3f
        val lineWidth = spacing * 0.09f

        // 高音谱表五条线
        for (line in 0..4) {
            val y = trebleBottomY - spacing * line
            drawLine(lineColor, Offset(staffLeft, y), Offset(staffRight, y), strokeWidth = lineWidth)
        }
        // 低音谱表五条线
        for (line in 0..4) {
            val y = bassTopY + spacing * line
            drawLine(lineColor, Offset(staffLeft, y), Offset(staffRight, y), strokeWidth = lineWidth)
        }
        // 左侧连接竖线
        drawLine(
            lineColor,
            Offset(staffLeft, trebleBottomY - spacing * 4),
            Offset(staffLeft, bassTopY + spacing * 4),
            strokeWidth = lineWidth * 1.6f,
        )

        // 谱号示意
        drawTrebleClefHint(
            leftX = staffLeft + spacing * 0.3f,
            secondLineY = trebleBottomY - spacing,
            spacing = spacing,
            color = accentColor,
        )
        drawBassClefHint(
            leftX = staffLeft + spacing * 0.3f,
            fourthLineY = bassTopY + spacing,
            spacing = spacing,
            color = accentColor,
        )

        // 中央 C：两行谱中间的共享加线 + 符头
        val cX = staffLeft + (staffRight - staffLeft) * 0.55f
        drawLine(
            lineColor,
            Offset(cX - spacing * 0.9f, middleCY),
            Offset(cX + spacing * 0.9f, middleCY),
            strokeWidth = lineWidth,
        )
        drawOval(
            accentColor,
            topLeft = Offset(cX - spacing * 0.55f, middleCY - spacing * 0.38f),
            size = Size(spacing * 1.1f, spacing * 0.76f),
        )
        val cLabel = textMeasurer.measure("中央 C", style = labelStyle.copy(color = accentColor))
        drawText(cLabel, topLeft = Offset(cX + spacing * 1.1f, middleCY - cLabel.size.height / 2f))

        // 右侧分工标注
        val rightX = staffRight + spacing * 0.4f
        val trebleLabel =
            textMeasurer.measure("高音谱表 · 右手", style = labelStyle.copy(color = labelColor))
        drawText(
            trebleLabel,
            topLeft = Offset(rightX, trebleBottomY - spacing * 2 - trebleLabel.size.height / 2f),
        )
        val bassLabel =
            textMeasurer.measure("低音谱表 · 左手", style = labelStyle.copy(color = labelColor))
        drawText(
            bassLabel,
            topLeft = Offset(rightX, bassTopY + spacing * 2 - bassLabel.size.height / 2f),
        )
    }
}

/**
 * 高音谱号（G 谱号）路径，取自 Wikimedia Commons 的 GClef.svg
 * （LilyPond Feta 字体字形，公有领域）：https://commons.wikimedia.org/wiki/File:GClef.svg
 */
private const val TREBLE_CLEF_PATH_DATA =
    "m12.049 3.5296c0.305 3.1263-2.019 5.6563-4.0772 7.7014-0.9349 0.897-0.155 0.148-0.6437 0.594" +
        "-0.1022-0.479-0.2986-1.731-0.2802-2.11 0.1304-2.6939 2.3198-6.5875 4.2381-8.0236 0.309" +
        " 0.5767 0.563 0.6231 0.763 1.8382zm0.651 16.142c-1.232-0.906-2.85-1.144-4.3336-0.885" +
        "-0.1913-1.255-0.3827-2.51-0.574-3.764 2.3506-2.329 4.9066-5.0322 5.0406-8.5394 0.059" +
        "-2.232-0.276-4.6714-1.678-6.4836-1.7004 0.12823-2.8995 2.156-3.8019 3.4165-1.4889" +
        " 2.6705-1.1414 5.9169-0.57 8.7965-0.8094 0.952-1.9296 1.743-2.7274 2.734-2.3561" +
        " 2.308-4.4085 5.43-4.0046 8.878 0.18332 3.334 2.5894 6.434 5.8702 7.227 1.2457 0.315" +
        " 2.5639 0.346 3.8241 0.099 0.2199 2.25 1.0266 4.629 0.0925 6.813-0.7007 1.598-2.7875" +
        " 3.004-4.3325 2.192-0.5994-0.316-0.1137-0.051-0.478-0.252 1.0698-0.257 1.9996-1.036" +
        " 2.26-1.565 0.8378-1.464-0.3998-3.639-2.1554-3.358-2.262 0.046-3.1904 3.14-1.7356 4.685" +
        " 1.3468 1.52 3.833 1.312 5.4301 0.318 1.8125-1.18 2.0395-3.544 1.8325-5.562-0.07-0.678" +
        "-0.403-2.67-0.444-3.387 0.697-0.249 0.209-0.059 1.193-0.449 2.66-1.053 4.357-4.259" +
        " 3.594-7.122-0.318-1.469-1.044-2.914-2.302-3.792zm0.561 5.757c0.214 1.991-1.053" +
        " 4.321-3.079 4.96-0.136-0.795-0.172-1.011-0.2626-1.475-0.4822-2.46-0.744-4.987-1.116" +
        "-7.481 1.6246-0.168 3.4576 0.543 4.0226 2.184 0.244 0.577 0.343 1.197 0.435 1.812z" +
        "m-5.1486 5.196c-2.5441 0.141-4.9995-1.595-5.6343-4.081-0.749-2.153-0.5283-4.63" +
        " 0.8207-6.504 1.1151-1.702 2.6065-3.105 4.0286-4.543 0.183 1.127 0.366 2.254 0.549" +
        " 3.382-2.9906 0.782-5.0046 4.725-3.215 7.451 0.5324 0.764 1.9765 2.223 2.7655 1.634" +
        "-1.102-0.683-2.0033-1.859-1.8095-3.227-0.0821-1.282 1.3699-2.911 2.6513-3.198 0.4384" +
        " 2.869 0.9413 6.073 1.3797 8.943-0.5054 0.1-1.0211 0.143-1.536 0.143z"

/** 高音谱号：SVG 纵向 6.02 个单位折合成五线谱一个线距（谱号全高约 6.8 个线距）。 */
private const val TREBLE_UNITS_PER_SPACE = 6.02f

/** 高音谱号：螺旋中心在 SVG 坐标系中的 y，对齐第二线（G 线）。 */
private const val TREBLE_SPIRAL_CENTER_Y = 25.899f

/**
 * 低音谱号（F 谱号）路径，取自 Wikimedia Commons 的 FClef.svg
 * （作者 Wikimedia 用户「っ」，CC BY 2.5）：https://commons.wikimedia.org/wiki/File:FClef.svg
 * 三条子路径依次为：上圆点、下圆点、逗号形主体。
 */
private const val BASS_CLEF_PATH_DATA =
    "M 248.25999,536.80200 C 248.26766,537.17138 248.11044,537.54065 247.82878,537.78185" +
        " C 247.46853,538.11076 246.91933,538.17813 246.47048,538.01071 C 246.02563,537.83894" +
        " 245.69678,537.39883 245.67145,536.92060 C 245.63767,536.54689 245.75685,536.15479" +
        " 246.02747,535.88867 C 246.28257,535.61680 246.66244,535.48397 247.03147,535.50645" +
        " C 247.41131,535.51452 247.77805,535.70601 248.00489,536.01019 C 248.17962,536.23452" +
        " 248.26238,536.51954 248.25999,536.80200 z" +
        " M 248.25999,542.64502 C 248.26772,543.01469 248.11076,543.38446 247.82878,543.62585" +
        " C 247.46853,543.95476 246.91933,544.02213 246.47048,543.85472 C 246.02537,543.68288" +
        " 245.69655,543.24237 245.67145,542.76389 C 245.63651,542.38990 245.76354,542.00308" +
        " 246.02700,541.73300 C 246.27663,541.45454 246.66060,541.32790 247.02845,541.34950" +
        " C 247.51230,541.36282 247.95159,541.69251 248.15162,542.12465 C 248.22565,542.28740" +
        " 248.26043,542.46657 248.25999,542.64502 z" +
        " M 243.97900,540.86798 C 244.02398,543.69258 242.76360,546.43815 240.76469,548.40449" +
        " C 238.27527,550.89277 235.01791,552.47534 231.69762,553.53261 C 231.25590,553.77182" +
        " 230.58970,553.45643 231.28550,553.13144 C 232.62346,552.52289 234.01319,552.00050" +
        " 235.24564,551.18080 C 237.96799,549.49750 240.26523,546.84674 240.82279,543.61854" +
        " C 241.14771,541.65352 241.05724,539.60795 240.56484,537.67852 C 240.20352,536.25993" +
        " 239.22033,534.79550 237.66352,534.58587 C 236.25068,534.36961 234.74885,534.85905" +
        " 233.74057,535.88093 C 233.47541,536.14967 232.95916,536.89403 233.04435,537.74747" +
        " C 233.64637,537.27468 233.60528,537.32732 234.09900,537.10717 C 235.23573,536.60031" +
        " 236.74349,537.32105 237.02700,538.57272 C 237.32909,539.72295 237.09551,541.18638" +
        " 235.96036,541.79960 C 234.77512,542.44413 233.02612,542.17738 232.36450,540.90866" +
        " C 231.26916,538.95418 231.87147,536.28193 233.64202,534.92571 C 235.44514,533.42924" +
        " 238.07609,533.37089 240.19963,534.13862 C 242.38419,534.95111 243.68629,537.21483" +
        " 243.89691,539.45694 C 243.95419,539.92492 243.97896,540.39668 243.97900,540.86798 z"

/** 低音谱号：两个圆点的中心距（SVG 单位），恰等于一个线距。 */
private const val BASS_DOT_SPAN = 5.843f

/** 低音谱号：圆点中点在归一化（见 [BASS_SVG_OFFSET_X]/[BASS_SVG_OFFSET_Y]）后的 y，对齐第四线（F 线）。 */
private const val BASS_DOTS_MID_Y = 6.064f

/** 低音谱号：原 SVG 内 group 的平移量，用于把原始坐标归一化到符号左上角。 */
private const val BASS_SVG_OFFSET_X = -230.9546f
private const val BASS_SVG_OFFSET_Y = -533.6597f

/** 大谱表中高低音谱号相对于五线谱线距的缩放比例（使谱号紧凑、协调且不超出卡片范围）。 */
private const val CLEF_SCALE = 0.75f

private val trebleClefPath: Path by lazy { PathParser().parsePathString(TREBLE_CLEF_PATH_DATA).toPath() }
private val bassClefPath: Path by lazy { PathParser().parsePathString(BASS_CLEF_PATH_DATA).toPath() }

/**
 * 高音谱号（G 谱号）示意：真实字形填充绘制，螺旋中心压在第二线（G 线）上。
 *
 * @param leftX 谱号左缘 x 坐标
 * @param secondLineY 五线谱第二线（自底向上，G 线）的 y 坐标
 * @param spacing 线距
 * @param color 绘制颜色
 */
private fun DrawScope.drawTrebleClefHint(
    leftX: Float,
    secondLineY: Float,
    spacing: Float,
    color: Color,
) {
    val scale = (spacing / TREBLE_UNITS_PER_SPACE) * CLEF_SCALE
    withTransform({
        translate(leftX, secondLineY - TREBLE_SPIRAL_CENTER_Y * scale)
        scale(scale, scale, Offset.Zero)
    }) {
        drawPath(trebleClefPath, color)
    }
}

/**
 * 低音谱号（F 谱号）示意：真实字形填充绘制，两个圆点分别贴住第四线（自底向上，F 线）上下两侧。
 *
 * @param leftX 谱号左缘 x 坐标
 * @param fourthLineY 五线谱第四线（自底向上，F 线）的 y 坐标
 * @param spacing 线距
 * @param color 绘制颜色
 */
private fun DrawScope.drawBassClefHint(
    leftX: Float,
    fourthLineY: Float,
    spacing: Float,
    color: Color,
) {
    val scale = (spacing / BASS_DOT_SPAN) * CLEF_SCALE
    withTransform({
        translate(leftX, fourthLineY - BASS_DOTS_MID_Y * scale)
        scale(scale, scale, Offset.Zero)
        translate(BASS_SVG_OFFSET_X, BASS_SVG_OFFSET_Y)
    }) {
        drawPath(bassClefPath, color)
    }
}

// endregion

// region 互动小课堂

/** 互动小课堂的一步；[target] 为 null 表示纯讲解步（点「下一步」继续）。 */
private data class LessonStep(
    val prompt: String,
    val target: StaffNote? = null,
    val hint: String? = null,
)

private val lessonSteps =
    listOf(
        LessonStep("欢迎来到互动小课堂！接下来我会一步步带你在高音谱表上找音，点错也没关系。"),
        LessonStep(
            "先找到 do（中央 C）",
            target = StaffNote(0),
            hint = "提示：do 躲在下加一线上——五线谱最下方那条短短的小线。",
        ),
        LessonStep(
            "这次找 mi",
            target = StaffNote(2),
            hint = "提示：mi 在第一线——最下面那条横线。",
        ),
        LessonStep(
            "找一找 sol",
            target = StaffNote(4),
            hint = "提示：sol 在第二线，从下往上数第二条横线。",
        ),
        LessonStep(
            "来点难的：高音 do",
            target = StaffNote(7),
            hint = "提示：它是 do 的高八度，住在第三间。",
        ),
        LessonStep(
            "最后一题：si",
            target = StaffNote(6),
            hint = "提示：si 在第三线——正中间那条线。",
        ),
    )

private val lessonPraises = listOf("对了！", "很好！", "漂亮！", "没错！")

/**
 * 互动小课堂：逐步引导用户在五线谱上点出指定音符。
 *
 * 点对显示绿色光晕并自动进入下一步；点错显示红色光晕，
 * 报出点错的音名并给出位置提示。全部完成后可重新开始或跳转练习页。
 *
 * @param onGoQuiz 完成页「去练习挑战」点击回调
 * @param modifier 布局修饰符
 */
@Composable
private fun GuidedLesson(
    onGoQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lessonNotes = remember { (0..7).map(::StaffNote) }
    var stepIndex by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }
    var lastWrong by remember { mutableStateOf<StaffNote?>(null) }
    var justCorrect by remember { mutableStateOf(false) }
    var praise by remember { mutableStateOf(lessonPraises.first()) }

    val step = lessonSteps.getOrNull(stepIndex)

    fun onNoteTap(note: StaffNote) {
        // 关键：从 stepIndex 委托状态现算目标（调用时读到当前值），
        // 而不是捕获按重组计算的 step 局部 val——pointerInput 手势块
        // 持有旧闭包时，step 会停留在上一题的目标音（实测复现）
        val target = lessonSteps.getOrNull(stepIndex)?.target ?: return
        if (justCorrect) return
        if (note == target) {
            praise = lessonPraises.random()
            lastWrong = null
            justCorrect = true
        } else {
            attempts++
            lastWrong = note
        }
    }

    // 点对停留片刻后进入下一步；最后一步则进入完成页
    LaunchedEffect(justCorrect) {
        if (justCorrect) {
            delay(900)
            justCorrect = false
            attempts = 0
            if (stepIndex >= lessonSteps.lastIndex) {
                completed = true
            } else {
                stepIndex++
            }
        }
    }
    // 点错的红色光晕短暂停留后消失
    LaunchedEffect(lastWrong) {
        if (lastWrong != null) {
            delay(800)
            lastWrong = null
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 进度条 + 步数
        val progress by animateFloatAsState(
            if (completed) 1f else stepIndex / lessonSteps.size.toFloat(),
            tween(400, easing = EmphasizedDecelerate),
            label = "lessonProgress",
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
            )
            Text(
                text =
                if (completed) {
                    "完成"
                } else {
                    "${(stepIndex + 1).coerceAtMost(lessonSteps.size)}/${lessonSteps.size}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (completed) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "教学完成！",
                    style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "你已经能自己在五线谱上找到 do、mi、sol、si 和高音 do 了。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(
                        onClick = {
                            completed = false
                            stepIndex = 0
                            attempts = 0
                            lastWrong = null
                            justCorrect = false
                        },
                    ) {
                        Text("再学一遍")
                    }
                    Button(onClick = onGoQuiz) {
                        Text("去练习挑战")
                    }
                }
            }
        } else if (step != null) {
            // 引导语：换步时交叉淡入 + 上移
            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    (
                        fadeIn(tween(200)) +
                            slideInVertically(
                                tween(260, easing = EmphasizedDecelerate),
                            ) { it / 5 }
                        ) togetherWith fadeOut(tween(140))
                },
                label = "lessonPrompt",
            ) { index ->
                Text(
                    text = lessonSteps[index].prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            StaffCanvas(
                notes = lessonNotes,
                selectedNote = if (justCorrect) step.target else lastWrong,
                haloTint =
                if (justCorrect) {
                    CorrectContainer
                } else {
                    MaterialTheme.colorScheme.error
                },
                onNoteClick = if (step.target != null) ::onNoteTap else null,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            )

            if (step.target == null) {
                FilledTonalButton(
                    onClick = { stepIndex++ },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("下一步")
                }
            } else {
                // 反馈行：答对夸奖 / 答错报音名 + 位置提示（首次答错后才出现）
                val wrong = lastWrong
                val feedback: Pair<String, Color>? =
                    when {
                        justCorrect -> praise to MaterialTheme.colorScheme.primary

                        wrong != null ->
                            "那是 ${wrong.solfege.label}（${positionText(wrong.step)}），再找找看。" to
                                MaterialTheme.colorScheme.error

                        attempts > 0 && step.hint != null ->
                            step.hint to MaterialTheme.colorScheme.onSurfaceVariant

                        else -> null
                    }
                AnimatedContent(
                    targetState = feedback,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "lessonFeedback",
                ) { current ->
                    Text(
                        text = current?.first ?: "点一点五线谱上的音符",
                        style = MaterialTheme.typography.bodySmall,
                        color = current?.second ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// endregion

// endregion

// region 练习

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

private fun optionState(
    note: StaffNote,
    question: StaffQuiz.Question,
    picked: StaffNote?,
): OptionState = when {
    picked == null -> OptionState.Idle
    note == question.answer -> OptionState.Correct
    note == picked -> OptionState.Wrong
    else -> OptionState.Dimmed
}

@Composable
private fun QuizTab(modifier: Modifier = Modifier) {
    val generator = remember { StaffQuiz.Generator(random = Random.Default) }
    var direction by remember { mutableStateOf(Direction.NOTE_TO_SOLFEGE) }
    var question by remember { mutableStateOf(generator.next(direction)) }
    var picked by remember { mutableStateOf<StaffNote?>(null) }
    var total by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var streak by remember { mutableIntStateOf(0) }

    fun pick(note: StaffNote) {
        if (picked != null) return
        picked = note
        total++
        if (note == question.answer) {
            correct++
            streak++
        } else {
            streak = 0
        }
    }

    // 作答后停留片刻（看清反馈）再滑出下一题；答错多留一会儿看正确答案
    LaunchedEffect(picked) {
        if (picked != null) {
            delay(if (picked == question.answer) 600 else 1100)
            question = generator.next(direction, excludeStep = question.answer.step)
            picked = null
        }
    }

    Column(
        modifier =
        modifier
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 练习方向切换
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().testTag("staff_quiz_direction"),
        ) {
            Direction.entries.forEachIndexed { index, dir ->
                SegmentedButton(
                    selected = direction == dir,
                    onClick = {
                        if (direction != dir) {
                            direction = dir
                            question = generator.next(dir)
                            picked = null
                        }
                    },
                    shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = Direction.entries.size,
                    ),
                ) {
                    Text(
                        if (dir == Direction.NOTE_TO_SOLFEGE) "看谱认唱名" else "听名找位置",
                    )
                }
            }
        }

        StatsRow(total = total, correct = correct, streak = streak)

        // 题目切换：整体右滑进入、左滑退出
        AnimatedContent(
            targetState = question,
            transitionSpec = {
                (
                    slideInHorizontally(
                        tween(320, easing = EmphasizedDecelerate),
                    ) { it / 3 } + fadeIn(tween(260))
                    ) togetherWith
                    (
                        slideOutHorizontally(
                            tween(220, easing = EmphasizedAccelerate),
                        ) { -it / 3 } + fadeOut(tween(180))
                        )
            },
            label = "quizQuestion",
        ) { current ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                QuestionCard(current)

                // 选项逐个弹入（每换一题重新级联）
                var shownCount by remember(current) { mutableIntStateOf(0) }
                LaunchedEffect(current) {
                    repeat(current.options.size) {
                        delay(60)
                        shownCount++
                    }
                }
                QuizOptions(
                    question = current,
                    picked = picked,
                    shownCount = shownCount,
                    onPick = ::pick,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatsRow(
    total: Int,
    correct: Int,
    streak: Int,
) {
    // 数字滚动 + 连击小幅弹跳
    val animatedTotal by animateIntAsState(total, label = "total")
    val animatedCorrect by animateIntAsState(correct, label = "correct")
    val animatedStreak by animateIntAsState(streak, label = "streak")
    val streakScale = remember { Animatable(1f) }
    LaunchedEffect(streak) {
        if (streak >= 2) {
            streakScale.animateTo(1.12f, tween(100))
            streakScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatChip(label = "已答 $animatedTotal", modifier = Modifier.weight(1f))
        StatChip(
            label =
            "正确率 " +
                if (total == 0) "--" else "${animatedCorrect * 100 / animatedTotal.coerceAtLeast(1)}%",
            modifier = Modifier.weight(1f),
        )
        StatChip(
            label = if (streak >= 2) "连击 ×$animatedStreak" else "连击 $animatedStreak",
            emphasized = streak >= 2,
            modifier =
            Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = streakScale.value
                    scaleY = streakScale.value
                },
        )
    }
}

@Composable
private fun StatChip(
    label: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val containerColor by animateColorAsState(
        if (emphasized) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        tween(250),
        label = "chipContainer",
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style =
            MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            ),
            color =
            if (emphasized) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuestionCard(question: StaffQuiz.Question) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors =
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text =
                if (question.direction == Direction.NOTE_TO_SOLFEGE) {
                    "这个音符唱作什么？"
                } else {
                    "在五线谱上找出这个音"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (question.direction == Direction.NOTE_TO_SOLFEGE) {
                StaffCanvas(
                    notes = listOf(question.answer),
                    noteColor = MaterialTheme.colorScheme.primary,
                    popKey = question,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
            } else {
                Text(
                    text = question.answer.solfege.label,
                    style =
                    MaterialTheme.typography.displayMedium.copy(
                        fontFamily = FontFamily.Serif,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun QuizOptions(
    question: StaffQuiz.Question,
    picked: StaffNote?,
    shownCount: Int,
    onPick: (StaffNote) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.testTag("staff_quiz_options"),
    ) {
        question.options.chunked(2).forEachIndexed { rowIndex, rowNotes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowNotes.forEachIndexed { columnIndex, note ->
                    val optionIndex = rowIndex * 2 + columnIndex
                    AnimatedVisibility(
                        visible = optionIndex < shownCount,
                        enter =
                        scaleIn(
                            spring(
                                dampingRatio = Spring.DampingRatioLowBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            initialScale = 0.85f,
                        ) + fadeIn(tween(150)),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (question.direction == Direction.NOTE_TO_SOLFEGE) {
                            SolfegeOption(
                                note = note,
                                state = optionState(note, question, picked),
                                onClick = { onPick(note) },
                            )
                        } else {
                            StaffOption(
                                note = note,
                                state = optionState(note, question, picked),
                                enabled = picked == null,
                                onClick = { onPick(note) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 选项反馈动画修饰符：答对轻微弹跳、答错小幅左右抖动。 */
@Composable
private fun rememberFeedbackModifier(state: OptionState): Modifier {
    val pop = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state) {
        when (state) {
            OptionState.Correct -> {
                pop.animateTo(1.06f, tween(100))
                pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
            }

            OptionState.Wrong -> {
                shake.animateTo(
                    0f,
                    keyframes {
                        durationMillis = 350
                        -10f at 50
                        8f at 120
                        -5f at 200
                        3f at 270
                        0f at 350
                    },
                )
            }

            else -> Unit
        }
    }
    return Modifier.graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
        translationX = shake.value.dp.toPx()
    }
}

@Composable
private fun SolfegeOption(
    note: StaffNote,
    state: OptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        when (state) {
            OptionState.Correct -> CorrectContainer
            OptionState.Wrong -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        tween(200),
        label = "optionContainer",
    )
    val contentColor by animateColorAsState(
        when (state) {
            OptionState.Correct -> OnCorrectContainer
            OptionState.Wrong -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        },
        tween(200),
        label = "optionContent",
    )
    Button(
        onClick = onClick,
        enabled = state == OptionState.Idle,
        shape = RoundedCornerShape(20.dp),
        colors =
        ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor,
        ),
        modifier =
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .alpha(if (state == OptionState.Dimmed) 0.45f else 1f)
            .then(rememberFeedbackModifier(state))
            .testTag("staff_quiz_option_${note.solfege.label}"),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = note.solfege.label,
                style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = "(${note.solfege.number})",
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier.alpha(0.65f),
            )
        }
    }
}

@Composable
private fun StaffOption(
    note: StaffNote,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        when (state) {
            OptionState.Correct -> CorrectContainer
            OptionState.Wrong -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        tween(200),
        label = "staffOptionContainer",
    )
    val noteColor =
        when (state) {
            OptionState.Correct -> OnCorrectContainer
            OptionState.Wrong -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier =
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .alpha(if (state == OptionState.Dimmed) 0.45f else 1f)
            .then(rememberFeedbackModifier(state)),
    ) {
        StaffCanvas(
            notes = listOf(note),
            noteColor = noteColor,
            modifier = Modifier.fillMaxSize().padding(8.dp),
        )
    }
}

// endregion

// region 五线谱画布

/** 五线谱画布的纵向布局度量：线距、五线位置与左右边界。 */
internal class StaffMetrics(
    val spacing: Float,
    val topLineY: Float,
    val bottomLineY: Float,
    val staffLeft: Float,
    val staffRight: Float,
)

/**
 * 计算五线谱画布纵向布局。[fitNote] 为 null 用传统固定布局（容纳 C4~C6）；
 * 非 null 时谱面刚好容纳「完整五线 + 该 step 的音符」：音符在五线内时五线
 * 撑满画布，超出五线后按需向外扩展（符头 + 光晕余量），不高不低时没有
 * 多余留白。余量对 fitNote 连续，选音变化时谱面平滑滑动缩放。
 */
internal fun staffMetrics(
    size: Size,
    fitNote: Float?,
): StaffMetrics {
    if (fitNote == null) {
        val spacing = size.height / 9f
        val inset = spacing * 0.9f
        return StaffMetrics(spacing, spacing * 2.4f, spacing * 6.4f, inset, size.width - inset)
    }
    // noteUnits：音符相对下一线的线距数。顶部至少容纳五线（4 线距 + 0.6 边距），
    // 底部至少 1.2 线距（覆盖中线以上音符的下行符干）；音符超出五线时再扩光晕余量
    val noteUnits = (fitNote - StaffGeometry.BOTTOM_LINE_STEP) / 2f
    val topRoom = (noteUnits + 1.5f).coerceAtLeast(4.6f)
    val bottomRoom = (1.5f - noteUnits).coerceAtLeast(1.2f)
    val spacing = size.height / (topRoom + bottomRoom)
    val bottomLineY = spacing * topRoom
    val inset = spacing * 0.9f
    return StaffMetrics(spacing, bottomLineY - spacing * 4f, bottomLineY, inset, size.width - inset)
}

/**
 * 五线谱画布：画五条线、加线、符头（倾斜椭圆）、符干与可选唱名标签。
 *
 * @param notes 要画的音符（1 个居中，多个横向均分）
 * @param selectedNote 选中音符（画光晕并轻微弹跳）
 * @param haloTint 选中光晕颜色；null 使用三级色
 * @param noteColor 音符颜色
 * @param labelFor 每个音符底部标签；null 不画标签
 * @param onNoteClick 点按音符回调；null 不可点按
 * @param animateEntrance true 时音符从左到右级联入场（教学阶梯用）
 * @param popKey 非 null 且变化时，音符轻微弹跳入场（练习出题用）
 * @param fitNote 非 null 时谱面自适应为「完整五线 + 该 step 音符」的最小布局，
 * 选音变化时平滑跟随（高音低音探索器用）；null 用固定布局
 */

@Composable
private fun StaffCanvas(
    notes: List<StaffNote>,
    modifier: Modifier = Modifier,
    selectedNote: StaffNote? = null,
    haloTint: Color? = null,
    noteColor: Color = MaterialTheme.colorScheme.onSurface,
    labelFor: ((StaffNote) -> String)? = null,
    onNoteClick: ((StaffNote) -> Unit)? = null,
    animateEntrance: Boolean = false,
    popKey: Any? = null,
    fitNote: Float? = null,
) {
    // pointerInput 以 notes 为 key 不随重组重启，直接捕获 onNoteClick 会拿到
    // 旧重组的闭包（互动小课堂里表现为还按上一题的目标音判定）。
    // rememberUpdatedState 保证手势块里读到的永远是最新回调。
    val currentOnNoteClick by rememberUpdatedState(onNoteClick)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val haloColor = haloTint ?: MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        TextStyle(
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = labelColor,
        )

    // 级联入场进度（0..1）
    val entrance = remember { Animatable(if (animateEntrance) 0f else 1f) }
    LaunchedEffect(animateEntrance, notes) {
        if (animateEntrance) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(700, easing = EmphasizedDecelerate))
        } else {
            entrance.snapTo(1f)
        }
    }
    // 出题弹跳
    val pop = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey != null) {
            pop.snapTo(0.7f)
            pop.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }
    // 选中音符弹跳
    val selectedPop = remember { Animatable(1f) }
    LaunchedEffect(selectedNote) {
        if (selectedNote != null) {
            selectedPop.snapTo(0.85f)
            selectedPop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
        }
    }
    // 选中光晕淡入
    val haloScale by animateFloatAsState(
        if (selectedNote != null) 1f else 0f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "halo",
    )

    Canvas(
        modifier =
        modifier
            // 画布默认不裁切，任何越界绘制（如动画过冲）都会画到兄弟组件上
            .clipToBounds()
            .then(
                if (onNoteClick == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(notes) {
                        detectTapGestures { tap ->
                            val metrics =
                                staffMetrics(
                                    Size(size.width.toFloat(), size.height.toFloat()),
                                    fitNote,
                                )
                            val spacing = metrics.spacing
                            val staffLeft = metrics.staffLeft
                            val staffRight = metrics.staffRight
                            val bottomLineY = metrics.bottomLineY
                            // 与下方绘制保持同一套坐标公式
                            val hit =
                                notes.indices.minByOrNull { i ->
                                    val x =
                                        staffLeft +
                                            (staffRight - staffLeft) * (i + 1) / (notes.size + 1f)
                                    val y =
                                        bottomLineY -
                                            StaffGeometry.halfUnitsFromBottomLine(notes[i].step) *
                                            spacing / 2f
                                    abs(tap.x - x) + abs(tap.y - y)
                                }
                            if (hit != null) {
                                val columnWidth = (staffRight - staffLeft) / (notes.size + 1f)
                                val x = staffLeft + columnWidth * (hit + 1)
                                if (abs(tap.x - x) < columnWidth) {
                                    currentOnNoteClick?.invoke(notes[hit])
                                }
                            }
                        }
                    }
                },
            ),
    ) {
        val metrics = staffMetrics(size, fitNote)
        val spacing = metrics.spacing
        val staffLeft = metrics.staffLeft
        val staffRight = metrics.staffRight
        val topLineY = metrics.topLineY
        val bottomLineY = metrics.bottomLineY

        // 五条线
        for (line in 0..4) {
            val y = topLineY + spacing * line
            drawLine(
                color = lineColor,
                start = Offset(staffLeft, y),
                end = Offset(staffRight, y),
                strokeWidth = spacing * 0.09f,
            )
        }

        // 音符密集时（如完整音位图）按列宽缩小符头，避免相邻符头粘连
        val columnWidth = (staffRight - staffLeft) / (notes.size + 1f)
        val headScale = (columnWidth / (spacing * 1.5f)).coerceIn(0.55f, 1f)
        val headWidth = spacing * 1.3f * headScale
        val headHeight = spacing * 0.9f * headScale
        notes.forEachIndexed { index, note ->
            // 入场缩放：级联 × 出题弹跳 × 选中弹跳
            val cascadeT =
                if (animateEntrance) {
                    ((entrance.value * (notes.size + 1)) - index).coerceIn(0f, 1f)
                } else {
                    1f
                }
            var scale = cascadeT * pop.value
            if (note == selectedNote) scale *= selectedPop.value
            if (scale <= 0.01f) return@forEachIndexed
            val alpha = scale.coerceIn(0f, 1f)

            val x = staffLeft + (staffRight - staffLeft) * (index + 1) / (notes.size + 1f)
            val offset = StaffGeometry.halfUnitsFromBottomLine(note.step)
            val y = bottomLineY - offset * spacing / 2f

            // 加线
            StaffGeometry.ledgerOffsets(note.step).forEach { ledgerOffset ->
                val ledgerY = bottomLineY - ledgerOffset * spacing / 2f
                drawLine(
                    color = lineColor.copy(alpha = alpha),
                    start = Offset(x - headWidth * 0.85f, ledgerY),
                    end = Offset(x + headWidth * 0.85f, ledgerY),
                    strokeWidth = spacing * 0.09f,
                )
            }

            // 选中光晕
            if (note == selectedNote && haloScale > 0.01f) {
                drawCircle(
                    color = haloColor.copy(alpha = 0.22f * haloScale),
                    radius = spacing * 1.35f * haloScale,
                    center = Offset(x, y),
                )
            }

            // 符干：中线（第三线）以下朝上，中线及以上朝下
            val stemUp = offset < 4
            val stemLength = spacing * 3.1f * scale
            val stemX = if (stemUp) x + headWidth * 0.42f * scale else x - headWidth * 0.42f * scale
            val stemEndY = if (stemUp) y - stemLength else y + stemLength
            drawLine(
                color = noteColor.copy(alpha = alpha),
                start = Offset(stemX, y),
                end = Offset(stemX, stemEndY),
                strokeWidth = spacing * 0.12f,
                cap = StrokeCap.Round,
            )

            // 符头：略倾斜的实心椭圆（四分音符）
            rotate(degrees = -16f, pivot = Offset(x, y)) {
                drawOval(
                    color = noteColor.copy(alpha = alpha),
                    topLeft =
                    Offset(
                        x - headWidth * scale / 2f,
                        y - headHeight * scale / 2f,
                    ),
                    size = Size(headWidth * scale, headHeight * scale),
                )
            }

            // 唱名标签：多音符时纵向错开（偶数行贴底、奇数行上移），避免相邻标签重叠
            if (labelFor != null) {
                val layout =
                    textMeasurer.measure(
                        labelFor(note),
                        style = labelStyle.copy(color = labelColor.copy(alpha = alpha)),
                    )
                val row = if (notes.size > 8) index % 2 else 0
                drawText(
                    textLayoutResult = layout,
                    topLeft =
                    Offset(
                        x - layout.size.width / 2f,
                        size.height - layout.size.height * (row + 1f),
                    ),
                )
            }
        }
    }
}

// endregion
