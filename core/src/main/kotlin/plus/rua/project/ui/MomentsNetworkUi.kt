package plus.rua.project.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import plus.rua.project.MomentsConnection

/** 加载进度、失败重试与分页入口共用同一显示规则。 */
@Composable
internal fun MomentsLoadStatus(loading: Boolean, error: String?, hasMore: Boolean, onRefresh: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
        Row {
            TextButton(onClick = onRefresh, enabled = !loading) { Text(if (error == null) "刷新" else "重试") }
            if (hasMore) TextButton(onClick = onMore, enabled = !loading) { Text("加载更多") }
        }
    }
}

/** 仅在页面前台时轮询，离开页面自动取消。 */
@Composable
internal fun MomentsPoll(key: Any?, onRefresh: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(lifecycle, key) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(15000)
                refresh()
            }
        }
    }
}

/** 账号选择页的连接设置；保存成功后触发 onSaved，关闭时触发 onDismiss。 */
@Composable
internal fun MomentsConnectionDialog(onSaved: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(MomentsConnection.url(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("朋友圈连接设置") }, text = {
        Column {
            OutlinedTextField(value = url, onValueChange = {
                url = it
                error = null
            }, label = { Text("服务地址") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton(onClick = {
            try {
                MomentsConnection.save(context, url)
                onSaved()
            } catch (e: Exception) {
                error = e.message ?: "地址无效"
            }
        }) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
