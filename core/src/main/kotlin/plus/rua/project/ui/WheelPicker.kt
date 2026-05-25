package plus.rua.project.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlin.math.abs

private val ItemHeight = 48.dp
private const val VisibleItemCount = 5
private val WheelHeight = ItemHeight * VisibleItemCount
private const val PaddingItems = VisibleItemCount / 2

/**
 * 通用滚轮选择器，支持惯性吸附和触觉反馈。
 *
 * 滚动停止后才触发选中变更和触觉反馈，避免快速滑动时抖动。
 *
 * @param items 显示的项目列表
 * @param selectedIndex 当前选中项索引
 * @param onSelectedChange 选中项变化回调（仅在滚动停止后触发）
 * @param modifier 外部布局修饰符
 */
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selectedIndex - PaddingItems).coerceAtLeast(0)
    )
    val view = LocalView.current

    // 视觉中心项（实时，仅用于渲染高亮）
    val visualCenter by remember {
        derivedStateOf {
            val viewportCenter = listState.layoutInfo.viewportSize.height / 2f
            listState.layoutInfo.visibleItemsInfo.minByOrNull {
                abs(it.offset + it.size / 2f - viewportCenter)
            }?.index?.let { it - PaddingItems } ?: selectedIndex
        }
    }

    // 初始滚动到选中项
    LaunchedEffect(selectedIndex) {
        val target = (selectedIndex - PaddingItems).coerceAtLeast(0)
        if (listState.firstVisibleItemIndex != target) {
            listState.scrollToItem(target)
        }
    }

    // 滚动停止后：计算最终中心项 → 触发选中变更 + 触觉反馈
    LaunchedEffect(listState) {
        var lastSettled = selectedIndex
        snapshotFlow { listState.isScrollInProgress to listState.layoutInfo}
            .collect { (scrolling, _) ->
                if (!scrolling) {
                    val center = visualCenter.coerceIn(0, items.lastIndex)
                    if (center != lastSettled) {
                        lastSettled = center
                        onSelectedChange(center)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
    }

    val snapLayoutInfoProvider = remember(listState) {
        SnapLayoutInfoProvider(listState)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.height(WheelHeight),
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        horizontalAlignment = Alignment.CenterHorizontally,
        userScrollEnabled = true
    ) {
        items(items.size + PaddingItems * 2) { layoutIndex ->
            val centerIndex = layoutIndex - PaddingItems
            val isValid = centerIndex in items.indices
            Box(
                modifier = Modifier
                    .height(ItemHeight)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isValid) {
                    val isSelected = centerIndex == visualCenter
                    Text(
                        text = items[centerIndex],
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontSize = if (isSelected) 20.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        style = LocalTextStyle.current
                    )
                }
            }
        }
    }
}
