package plus.rua.project.ui

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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${year}年${month}月",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "第${weekNumber}周",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
