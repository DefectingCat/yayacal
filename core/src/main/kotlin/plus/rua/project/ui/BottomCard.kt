package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import plus.rua.project.CalendarViewModel
import plus.rua.project.ShiftKind

/**
 * 底部卡片，折叠状态下支持垂直拖拽触发折叠动画。
 *
 * 卡片顶部显示拖拽把手，下方展示选中日期信息：
 * 左侧为相对今天的天数描述（A）和公历日期（B），
 * 右侧为农历日期（C）。
 *
 * @param viewModel 日历 ViewModel，用于读取折叠状态和驱动拖拽
 * @param selectedDate 当前选中的日期
 * @param today 今天的日期
 * @param dragRangePx 拖拽手势映射范围（像素），progress 从 0→1 对应手指移动此距离。
 *   应设为折叠时日历实际高度变化量 (weeks-1)×rowHeight，使拖拽跟手。
 * @param modifier 外部布局修饰符
 */
@Composable
fun BottomCard(
    viewModel: CalendarViewModel,
    selectedDate: LocalDate,
    today: LocalDate,
    dragRangePx: Float,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val relativeDesc = relativeDayDescription(selectedDate, today)

    @Suppress("DEPRECATION") // monthNumber 无替代 API，kotlinx-datetime 尚未提供新接口
    val solarDesc = "${selectedDate.monthNumber}月${selectedDate.day}日"
    val lunarDesc = formatLunarDate(selectedDate)
    val shiftMessage = when (viewModel.shiftKindAt(selectedDate)) {
        ShiftKind.WORK -> "小小上班，轻松拿下！"
        ShiftKind.OFF -> "耶耶耶，美美休息！"
        null -> null
    }

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
        Column(modifier = Modifier.fillMaxSize()) {
            // 拖拽把手
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .fillMaxWidth(0.15f)
                    .height(4.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // A / B / C 信息行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：A（相对天数）和 B（公历日期）在同一行
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = relativeDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = solarDesc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
                // 右侧：C（农历日期）
                Text(
                    text = lunarDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            if (shiftMessage != null) {
                Text(
                    text = shiftMessage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedGif(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.CenterHorizontally),
                contentDescription = "可爱小狗",
                seed = selectedDate,
            )
        }
    }
}
