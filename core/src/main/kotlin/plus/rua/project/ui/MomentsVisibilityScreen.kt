package plus.rua.project.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 双账号可见性设置：公开给两个账号，私密仅当前账号可见。
 * @param currentVisibility 已保存的公开/私密选项
 * @param onDone 点击完成后回传选项；不使用联系人标签，第二个参数始终为空
 * @param onCancel 点击取消时触发，不保存选择
 * @param modifier 布局修饰符
 */
@Composable
fun MomentsVisibilityScreen(currentVisibility: String, onDone: (String, List<String>) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    var visibility by remember(currentVisibility) { mutableStateOf(currentVisibility) }
    Column(modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("取消") }
            Text("谁可以看", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDone(visibility, emptyList()) }) { Text("完成") }
        }
        listOf("公开" to "小白和小鸡毛都能看到", "私密" to "仅当前账号可见").forEach { (value, description) ->
            Card(onClick = { visibility = value }, elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    RadioButton(selected = visibility == value, onClick = null)
                    Column {
                        Text(if (value == "私密") "仅自己可见" else value)
                        Text(description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
