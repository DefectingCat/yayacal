package plus.rua.project.ui

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import plus.rua.project.DayCellInfo
import plus.rua.project.LunarCache
import plus.rua.project.ShiftKind
import plus.rua.project.shared.R

enum class DayCellState {
    NORMAL, OTHER_MONTH, TODAY, SELECTED, SELECTED_TODAY
}

/**
 * 单个日期单元格，显示日期数字并支持选中/今天/非当月状态；玫瑰日日期左上角显示玫瑰图标，
 * 生日日期左上角显示金色皇冠；若两者重合则优先显示玫瑰图标。
 *
 * @param date 日期
 * @param isCurrentMonth 是否属于当前显示月份
 * @param isSelected 是否为选中日期
 * @param isToday 是否为今天
 * @param shiftKind 个人轮班类型;null 表示不显示。与法定调休完全独立。
 * @param showLegalHoliday 是否显示法定调休背景色。
 *   false(默认):排班放右上角,不显示法定调休背景。
 *   true:排班仍在右上角,法定假日以淡色背景显示("休"淡红,"班"淡蓝)。
 * @param holidayEdgeInfo 假日在连续序列中的边缘状态,决定背景圆角。null 表示无假日。
 * @param cellIndex 单元格在月网格中的线性索引(weekIndex*7+dayIndex),用于法定假日波浪动画延迟。
 * @param onClick 点击回调
 * @param interactionSource 点击交互源
 * @param lunarCache 农历缓存实例
 * @param lunarData 预计算的日期信息；null 时内部自动获取
 * @param modifier 外部布局修饰符
 */
@Composable
fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    shiftKind: ShiftKind?,
    showLegalHoliday: Boolean,
    holidayEdgeInfo: HolidayEdgeInfo? = null,
    cellIndex: Int = 0,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    lunarCache: LunarCache = LunarCache.default,
    lunarData: DayCellInfo? = null,
    modifier: Modifier = Modifier,
) {
    if (lunarData != null) {
        DayCellImpl(
            date = date,
            isCurrentMonth = isCurrentMonth,
            isSelected = isSelected,
            isToday = isToday,
            shiftKind = shiftKind,
            showLegalHoliday = showLegalHoliday,
            holidayEdgeInfo = holidayEdgeInfo,
            cellIndex = cellIndex,
            onClick = onClick,
            interactionSource = interactionSource,
            lunarData = lunarData,
            modifier = modifier,
        )
    } else {
        val computed by produceState(
            initialValue = DayCellInfo("", false, null),
            key1 = date,
            key2 = lunarCache
        ) {
            value = lunarCache.getOrCompute(date)
        }
        DayCellImpl(
            date = date,
            isCurrentMonth = isCurrentMonth,
            isSelected = isSelected,
            isToday = isToday,
            shiftKind = shiftKind,
            showLegalHoliday = showLegalHoliday,
            holidayEdgeInfo = holidayEdgeInfo,
            cellIndex = cellIndex,
            onClick = onClick,
            interactionSource = interactionSource,
            lunarData = computed,
            modifier = modifier,
        )
    }
}

@Composable
private fun DayCellImpl(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    shiftKind: ShiftKind?,
    showLegalHoliday: Boolean,
    holidayEdgeInfo: HolidayEdgeInfo?,
    cellIndex: Int,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    lunarData: DayCellInfo,
    modifier: Modifier,
) {
    val isBirthday = lunarData.isBirthday
    var birthdayClickTick by remember(date) { mutableIntStateOf(0) }
    val crownScale = remember(date) { Animatable(1f) }
    LaunchedEffect(birthdayClickTick) {
        if (birthdayClickTick > 0) {
            crownScale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            crownScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val isRoseDay = lunarData.isRoseDay
    var roseClickTick by remember(date) { mutableIntStateOf(0) }
    val roseScale = remember(date) { Animatable(1f) }
    LaunchedEffect(roseClickTick) {
        if (roseClickTick > 0) {
            roseScale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            roseScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        }
    }

    val annotationText = lunarData.annotationText
    val isAnnotationHighlight = lunarData.isAnnotationHighlight
    val holidayBadge = lunarData.holidayBadge
    val currentState = when {
        isSelected && isToday -> DayCellState.SELECTED_TODAY
        isSelected -> DayCellState.SELECTED
        isToday -> DayCellState.TODAY
        !isCurrentMonth -> DayCellState.OTHER_MONTH
        else -> DayCellState.NORMAL
    }

    val transition = updateTransition(targetState = currentState, label = "DayCell")

    val revealProgress by transition.animateFloat(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "revealProgress"
    ) { state ->
        when (state) {
            DayCellState.SELECTED, DayCellState.SELECTED_TODAY -> 1f
            else -> 0f
        }
    }

    val contentColor by transition.animateColor(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "contentColor"
    ) { state ->
        when (state) {
            DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer
            DayCellState.SELECTED -> MaterialTheme.colorScheme.primary
            DayCellState.TODAY -> MaterialTheme.colorScheme.primary
            DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            DayCellState.NORMAL -> MaterialTheme.colorScheme.onSurface
        }
    }

    // 选中今天:实心填充 primaryContainer;其他状态不填充。
    val selectedFillColor by transition.animateColor(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "selectedFillColor"
    ) { state ->
        when (state) {
            DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        }
    }

    // 选中非今天:绘制描边圆,避免遮挡右上角角标。
    val selectedOutlineAlpha by transition.animateFloat(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "selectedOutlineAlpha"
    ) { state ->
        when (state) {
            DayCellState.SELECTED -> 1f
            else -> 0f
        }
    }

    val selectedOutlineColor = MaterialTheme.colorScheme.primary

    val lunarColor by transition.animateColor(
        transitionSpec = { tween(150, easing = FastOutSlowInEasing) },
        label = "lunarColor"
    ) { state ->
        if (isAnnotationHighlight) {
            when (state) {
                DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.85f
                )
                DayCellState.SELECTED -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                DayCellState.TODAY -> MaterialTheme.colorScheme.primary
                DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                DayCellState.NORMAL -> MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            }
        } else {
            when (state) {
                DayCellState.SELECTED_TODAY -> MaterialTheme.colorScheme.onPrimaryContainer.copy(
                    alpha = 0.7f
                )
                DayCellState.SELECTED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                DayCellState.TODAY -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                DayCellState.OTHER_MONTH -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.26f)
                DayCellState.NORMAL -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            }
        }
    }

    val holidayBgColor = when (holidayBadge) {
        "休" -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        "班" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val holidayScale by animateFloatAsState(
        targetValue = if (showLegalHoliday && holidayBadge != null) 1f else 0f,
        animationSpec = tween(
            durationMillis = 200,
            delayMillis = cellIndex * 15,
            easing = FastOutSlowInEasing
        ),
        label = "holidayScale"
    )

    Box(
        modifier = modifier.aspectRatio(1f)
    ) {
        // 法定假日背景（最底层，与选中/今天状态叠加）
        if (holidayScale > 0f) {
            val holidayShape = when {
                holidayEdgeInfo?.isStart == true && holidayEdgeInfo.isEnd -> RoundedCornerShape(8.dp)
                holidayEdgeInfo?.isStart == true -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                holidayEdgeInfo?.isEnd == true -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.5.dp)
                    .graphicsLayer {
                        scaleX = holidayScale
                        scaleY = holidayScale
                        transformOrigin = TransformOrigin.Center
                    }
                    .background(holidayBgColor, holidayShape)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    val birthdaySuffix = if (isBirthday) "，生日" else ""
                    contentDescription = if (isToday) {
                        "今天 ${date.year}年${date.month.number}月${date.day}日$birthdaySuffix"
                    } else {
                        "${date.year}年${date.month.number}月${date.day}日$birthdaySuffix"
                    }
                }
                .clip(CircleShape)
                .drawBehind {
                    val maxRadius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    if (revealProgress > 0f && selectedFillColor.alpha > 0f) {
                        drawCircle(
                            color = selectedFillColor,
                            radius = revealProgress * maxRadius,
                            center = center
                        )
                    }
                    if (revealProgress > 0f && selectedOutlineAlpha > 0f) {
                        val strokePx = 1.5.dp.toPx()
                        drawCircle(
                            color = selectedOutlineColor.copy(alpha = selectedOutlineAlpha),
                            radius = revealProgress * maxRadius - strokePx / 2f,
                            center = center,
                            style = Stroke(width = strokePx)
                        )
                    }
                }
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        if (isRoseDay) {
                            roseClickTick += 1
                        } else if (isBirthday) {
                            birthdayClickTick += 1
                        }
                        onClick()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = date.day.toString(),
                    textAlign = TextAlign.Center,
                    color = contentColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = annotationText,
                    textAlign = TextAlign.Center,
                    color = lunarColor,
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    lineHeight = 9.sp
                )
            }
        }
        if (shiftKind != null) {
            val shiftAccentColor = if (shiftKind == ShiftKind.WORK) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
            val shiftLabel = if (shiftKind == ShiftKind.WORK) "班" else "休"
            val shiftAlpha = if (isCurrentMonth) 1f else 0.38f
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .zIndex(1f)
                    .padding(top = 1.dp, end = 2.dp)
                    .background(MaterialTheme.colorScheme.background, CircleShape)
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shiftLabel,
                    color = shiftAccentColor.copy(alpha = shiftAlpha),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 9.sp
                )
            }
        }
        if (isRoseDay) {
            Icon(
                painter = painterResource(R.drawable.ic_rose),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .padding(start = 2.dp, top = 2.dp)
                    .size(14.dp)
                    .graphicsLayer {
                        rotationZ = -45f
                        transformOrigin = TransformOrigin.Center
                        scaleX = roseScale.value
                        scaleY = roseScale.value
                    }
            )
        } else if (isBirthday) {
            Icon(
                painter = painterResource(R.drawable.ic_birthday_crown),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1f)
                    .padding(start = 2.dp, top = 2.dp)
                    .size(14.dp)
                    .graphicsLayer {
                        rotationZ = -45f
                        transformOrigin = TransformOrigin.Center
                        scaleX = crownScale.value
                        scaleY = crownScale.value
                    }
            )
        }
    }
}
