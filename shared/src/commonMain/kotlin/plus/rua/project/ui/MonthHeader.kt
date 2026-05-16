package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 月份标题栏，显示"年月"文字和 ISO 周号。
 *
 * @param year 年份
 * @param month 月份（1-12）
 * @param weekNumber 当前 ISO 周号
 * @param modifier 外部布局修饰符
 */
@Composable
fun MonthHeader(
    year: Int,
    month: Int,
    weekNumber: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp, horizontal = 12.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = Pair(year, month),
            transitionSpec = {
                if (targetState.second > initialState.second) {
                    slideInVertically(tween(250)) { -it } + fadeIn(tween(250)) togetherWith
                            slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
                } else {
                    slideInVertically(tween(250)) { it } + fadeIn(tween(250)) togetherWith
                            slideOutVertically(tween(250)) { -it } + fadeOut(tween(250))
                }
            }
        ) { (y, m) ->
            Text(
                text = "${y}年${m}月",
                style = MaterialTheme.typography.titleLarge
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = weekNumber,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInVertically(tween(250)) { -it } + fadeIn(tween(250)) togetherWith
                            slideOutVertically(tween(250)) { it } + fadeOut(tween(250))
                } else {
                    slideInVertically(tween(250)) { it } + fadeIn(tween(250)) togetherWith
                            slideOutVertically(tween(250)) { -it } + fadeOut(tween(250))
                }
            }
        ) { week ->
            Text(
                text = "第${week}周",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
