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
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
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
 * 可点按的 do~si 音阶阶梯与钢琴键盘（点按联动查看音名/唱名/谱面位置）。
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
                    0 -> TeachingTab(Modifier.fillMaxSize())
                    else -> QuizTab(Modifier.fillMaxSize())
                }
            }
        }
    }
}

// region 教学

@Composable
private fun TeachingTab(modifier: Modifier = Modifier) {
    // 一个八度的音阶阶梯：C4(do) ~ C5(do)
    val teachingNotes = remember { (0..7).map(::StaffNote) }
    var selected by remember { mutableStateOf(StaffNote(0)) }

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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text =
                    "${note.pitchName}${note.octave}" +
                        if (note.step == 0) " · 中央 C" else "",
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
        }
    }
}

/** 中文数字（谱面位置描述用，仅覆盖一到五，超出回退为阿拉伯数字）。 */
private fun cnNumeral(n: Int): String =
    listOf("一", "二", "三", "四", "五").getOrElse(n - 1) { n.toString() }

/** 五线谱位置的中文描述（第一线 / 第三间 / 下加一线 / 上加二间…）。 */
private fun positionText(step: Int): String {
    val offset = StaffGeometry.halfUnitsFromBottomLine(step)
    return when {
        offset == -1 -> "下加一间"
        offset <= -2 && offset % 2 == 0 -> "下加${cnNumeral(-offset / 2)}线"
        offset <= -3 -> "下加${cnNumeral((-offset - 1) / 2)}间"
        offset in 0..8 && offset % 2 == 0 -> "第${cnNumeral(offset / 2 + 1)}线"
        offset in 1..7 -> "第${cnNumeral((offset + 1) / 2)}间"
        offset == 9 -> "上加一间"
        offset % 2 == 0 -> "上加${cnNumeral((offset - 8) / 2)}线"
        else -> "上加${cnNumeral((offset - 7) / 2)}间"
    }
}

// endregion

// region 练习

private enum class OptionState { Idle, Correct, Wrong, Dimmed }

private fun optionState(
    note: StaffNote,
    question: StaffQuiz.Question,
    picked: StaffNote?,
): OptionState =
    when {
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
        Text(
            text = note.solfege.label,
            style =
            MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Bold,
            ),
        )
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

/**
 * 五线谱画布：画五条线、加线、符头（倾斜椭圆）、符干与可选唱名标签。
 *
 * @param notes 要画的音符（1 个居中，多个横向均分）
 * @param selectedNote 选中音符（画三级色光晕并轻微弹跳）
 * @param noteColor 音符颜色
 * @param labelFor 每个音符底部标签；null 不画标签
 * @param onNoteClick 点按音符回调；null 不可点按
 * @param animateEntrance true 时音符从左到右级联入场（教学阶梯用）
 * @param popKey 非 null 且变化时，音符轻微弹跳入场（练习出题用）
 */
@Composable
private fun StaffCanvas(
    notes: List<StaffNote>,
    modifier: Modifier = Modifier,
    selectedNote: StaffNote? = null,
    noteColor: Color = MaterialTheme.colorScheme.onSurface,
    labelFor: ((StaffNote) -> String)? = null,
    onNoteClick: ((StaffNote) -> Unit)? = null,
    animateEntrance: Boolean = false,
    popKey: Any? = null,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val haloColor = MaterialTheme.colorScheme.tertiary
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
        modifier.then(
            if (onNoteClick == null) {
                Modifier
            } else {
                Modifier.pointerInput(notes) {
                    detectTapGestures { tap ->
                        val spacing = size.height / 9f
                        val staffLeft = spacing * 0.9f
                        val staffRight = size.width - spacing * 0.9f
                        val bottomLineY = spacing * 6.4f
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
                                onNoteClick(notes[hit])
                            }
                        }
                    }
                }
            },
        ),
    ) {
        val spacing = size.height / 9f
        val staffLeft = spacing * 0.9f
        val staffRight = size.width - spacing * 0.9f
        val topLineY = spacing * 2.4f
        val bottomLineY = topLineY + spacing * 4f

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

        val headWidth = spacing * 1.3f
        val headHeight = spacing * 0.9f
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

            // 唱名标签
            if (labelFor != null) {
                val layout =
                    textMeasurer.measure(
                        labelFor(note),
                        style = labelStyle.copy(color = labelColor.copy(alpha = alpha)),
                    )
                drawText(
                    textLayoutResult = layout,
                    topLeft =
                    Offset(
                        x - layout.size.width / 2f,
                        size.height - layout.size.height,
                    ),
                )
            }
        }
    }
}

// endregion
