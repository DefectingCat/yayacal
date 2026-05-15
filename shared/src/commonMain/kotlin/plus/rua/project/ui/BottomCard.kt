package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import plus.rua.project.CalendarViewModel

/**
 * 底部卡片，折叠状态下支持垂直拖拽触发折叠动画。
 *
 * @param viewModel 日历 ViewModel，用于读取折叠状态和驱动拖拽
 * @param modifier 外部布局修饰符
 */
@Composable
fun BottomCard(
    viewModel: CalendarViewModel,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val dragRange = with(density) { DRAG_RANGE_DP.dp.toPx() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(viewModel.isCollapsed) {
                if (viewModel.isCollapsed) {
                    // 折叠状态：下拉恢复到月视图
                    detectVerticalDragGestures(
                        onDragEnd = {
                            viewModel.onExpandDragEnd()
                        },
                        onDragCancel = {
                            viewModel.onExpandDragEnd()
                        }
                    ) { _, dragAmount ->
                        val delta = -dragAmount / dragRange
                        viewModel.onExpandDrag(delta)
                    }
                } else {
                    // 展开状态：上拉折叠到周视图
                    detectVerticalDragGestures(
                        onDragEnd = {
                            viewModel.onDragEnd()
                        },
                        onDragCancel = {
                            viewModel.onDragEnd()
                        }
                    ) { _, dragAmount ->
                        val delta = -dragAmount / dragRange
                        viewModel.onDrag(delta)
                    }
                }
            },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray.copy(alpha = 0.4f))
                    .fillMaxWidth(0.15f)
                    .height(4.dp)
            )
        }
    }
}