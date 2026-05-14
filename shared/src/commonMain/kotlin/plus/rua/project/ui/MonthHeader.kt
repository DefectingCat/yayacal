package plus.rua.project.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        Text(
            text = " 第${weekNumber}周",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.sp
        )
    }
}
