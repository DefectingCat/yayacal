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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import plus.rua.project.CalendarViewModel

/**
 * 底部卡片，折叠状态下支持垂直拖拽触发折叠动画。
 *
 * @param viewModel 日历 ViewModel，用于读取折叠状态和驱动拖拽
 * @param dragRangePx 拖拽手势映射范围（像素），progress 从 0→1 对应手指移动此距离。
 *   应设为折叠时日历实际高度变化量 (weeks-1)×rowHeight，使拖拽跟手。
 * @param modifier 外部布局修饰符
 */
@Composable
fun BottomCard(
    viewModel: CalendarViewModel,
    dragRangePx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(viewModel.isCollapsed) {
                val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                if (viewModel.isCollapsed) {
                    // 折叠状态：下拉恢复到月视图
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            // 上滑为正（折叠方向），下拉为负（展开方向）
                            val velocityDpPerSec = with(density) { -velocity.y.toDp().value }
                            viewModel.onExpandDragEnd(velocityDpPerSec)
                        },
                        onDragCancel = {
                            viewModel.onExpandDragEnd()
                        }
                    ) { change, dragAmount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val delta = -dragAmount / dragRangePx
                        viewModel.onExpandDrag(delta)
                    }
                } else {
                    // 展开状态：上拉折叠到周视图
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            // 上滑为正（折叠方向），下拉为负（展开方向）
                            val velocityDpPerSec = with(density) { -velocity.y.toDp().value }
                            viewModel.onDragEnd(velocityDpPerSec)
                        },
                        onDragCancel = {
                            viewModel.onDragEnd()
                        }
                    ) { change, dragAmount ->
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        val delta = -dragAmount / dragRangePx
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
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .fillMaxWidth(0.15f)
                    .height(4.dp)
            )
        }
    }
}
