package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 月份标题栏，显示"年月"文字和 ISO 周号。
 *
 * @param year 年份
 * @param month 月份（1-12）
 * @param weekNumber 当前 ISO 周号
 * @param showToday 是否显示「今天」按钮（当 selectedDate ≠ today 时）
 * @param onToday 点击「今天」按钮跳转今天
 * @param modifier 外部布局修饰符
 */
@Composable
fun MonthHeader(
    year: Int,
    month: Int,
    weekNumber: Int,
    showToday: Boolean,
    onToday: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = Pair(year, month),
            transitionSpec = {
                if (targetState.second > initialState.second) {
                    slideInVertically(tween(250)) { -it } togetherWith
                            slideOutVertically(tween(250)) { it }
                } else {
                    slideInVertically(tween(250)) { it } togetherWith
                            slideOutVertically(tween(250)) { -it }
                } using SizeTransform { _, _ -> snap() }
            }
        ) { (y, m) ->
            Text(
                text = "${y}年${m}月",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = weekNumber,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically(tween(250)) { -it } togetherWith
                            slideOutVertically(tween(250)) { it }
                } else {
                    slideInVertically(tween(250)) { it } togetherWith
                            slideOutVertically(tween(250)) { -it }
                } using SizeTransform { _, _ -> snap() }
            },
            modifier = Modifier
        ) { week ->
            Text(
                text = "第${week}周",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        val todayAlpha by animateFloatAsState(
            targetValue = if (showToday && onToday != null) 1f else 0f,
            animationSpec = tween(200)
        )
        if (onToday != null) {
            Text(
                text = "今天",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 14.sp,
                modifier = Modifier
                    .graphicsLayer { alpha = todayAlpha }
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = showToday, onClick = onToday)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
