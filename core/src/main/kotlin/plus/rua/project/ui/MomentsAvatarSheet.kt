@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * 微信风格的头像操作菜单：居中选项、细分隔线和独立取消区域，支持恢复进度与失败重试。
 *
 * @param sheetState 控制菜单展开和收起的状态
 * @param isRestoring 恢复请求进行中时禁止重复操作，并显示进度
 * @param error 恢复失败时展示的说明，为空时显示菜单标题
 * @param onChoosePhoto 点击相册选项且菜单收起后触发
 * @param onRestoreDefault 点击恢复默认选项时触发，调用方负责请求及成功后的收起
 * @param onDismiss 点击取消、遮罩、返回键或下滑收起时触发；相册选项也会先触发此回调
 * @param modifier 菜单布局修饰符
 */
@Composable
internal fun MomentsAvatarSheet(
    sheetState: SheetState,
    isRestoring: Boolean,
    error: String?,
    onChoosePhoto: () -> Unit,
    onRestoreDefault: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val background = momentsBackgroundColor()
    fun dismissThen(action: () -> Unit = {}) {
        scope.launch {
            sheetState.hide()
            onDismiss()
            action()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        containerColor = background,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        modifier = modifier.testTag("moments_avatar_sheet"),
    ) {
        Column(Modifier.fillMaxWidth().background(background).navigationBarsPadding()) {
            Text(
                text = "头像设置",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            )
            error?.let {
                MomentsErrorNotice(it, title = "头像未能恢复", hint = "当前头像已保留，可以再次尝试恢复", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            AvatarActionRow(
                text = "从手机相册选择",
                enabled = !isRestoring,
                onClick = { dismissThen(onChoosePhoto) },
                modifier = Modifier.testTag("moments_avatar_choose_photo"),
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            AvatarActionRow(
                text = if (isRestoring) "正在恢复…" else "恢复默认头像",
                enabled = !isRestoring,
                isLoading = isRestoring,
                onClick = onRestoreDefault,
                modifier = Modifier.testTag("moments_avatar_reset"),
            )
            Spacer(Modifier.fillMaxWidth().height(8.dp).background(momentsBarColor()))
            AvatarActionRow(
                text = "取消",
                onClick = { dismissThen() },
                modifier = Modifier.testTag("moments_avatar_cancel"),
            )
        }
    }
}

/** 菜单操作行；点击启用的行时触发 onClick，加载中的行显示进度且不可重复点击。 */
@Composable
private fun AvatarActionRow(text: String, onClick: () -> Unit, enabled: Boolean = true, isLoading: Boolean = false, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        colors = CardDefaults.cardColors(
            containerColor = momentsBackgroundColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = momentsBackgroundColor(),
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (isLoading) {
                    MomentsLoadingSpinner(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(text = text, fontSize = 17.sp, color = LocalContentColor.current)
            }
        }
    }
}
