package plus.rua.project.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import plus.rua.project.MomentComment

/** 删除入口只属于已知的当前评论作者；旧记录缺失作者时不能猜测归属。 */
internal fun canDeleteMomentComment(comment: MomentComment, currentAccountId: String?): Boolean = !comment.deleted && !currentAccountId.isNullOrBlank() && comment.authorId == currentAccountId

/** 菜单优先放在评论上方，并避开屏幕边缘、状态栏及输入法。 */
internal class MomentCommentMenuPositionProvider(
    private val margin: Int,
    private val spacing: Int,
    private val topInset: Int = 0,
    private val bottomInset: Int = 0,
    private val onPositionCalculated: (Boolean) -> Unit = {},
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val minX = margin.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(minX)
        val minY = (topInset + margin).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
        val maxY = (windowSize.height - bottomInset - popupContentSize.height - margin).coerceAtLeast(minY)
        val above = anchorBounds.top - popupContentSize.height - spacing
        val below = above < minY
        val x = (anchorBounds.center.x - popupContentSize.width / 2).coerceIn(minX, maxX)
        val y = (if (below) anchorBounds.bottom + spacing else above).coerceIn(minY, maxY)
        onPositionCalculated(below)
        return IntOffset(x, y)
    }
}

/**
 * 锚定在评论行的微信式操作浮层。
 *
 * @param onCopy 点击复制时触发；为 null 时隐藏复制
 * @param onDelete 点击删除入口时触发，调用方随后展示确认框；为 null 时隐藏删除
 * @param onDismiss 点击外部或返回时触发
 * @param modifier 浮层内容修饰符
 */
@Composable
internal fun MomentsCommentActionsMenu(
    onCopy: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (onCopy == null && onDelete == null) return
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.roundToPx() }
    val spacing = with(density) { 4.dp.roundToPx() }
    val topInset = WindowInsets.safeDrawing.getTop(density)
    val bottomInset = WindowInsets.safeDrawing.getBottom(density)
    var below by remember { mutableStateOf(false) }
    val positionProvider = remember(margin, spacing, topInset, bottomInset) {
        MomentCommentMenuPositionProvider(margin, spacing, topInset, bottomInset) { below = it }
    }
    val menuColor = Color(0xFF292929)
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier.width(IntrinsicSize.Max).semantics { testTagsAsResourceId = true }.testTag("moments_comment_actions"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            @Composable
            fun Arrow() {
                Canvas(Modifier.size(width = 14.dp, height = 7.dp)) {
                    val path = Path().apply {
                        if (below) {
                            moveTo(0f, size.height)
                            lineTo(size.width / 2, 0f)
                            lineTo(size.width, size.height)
                        } else {
                            moveTo(0f, 0f)
                            lineTo(size.width / 2, size.height)
                            lineTo(size.width, 0f)
                        }
                        close()
                    }
                    drawPath(path, menuColor)
                }
            }
            if (below) Arrow()
            Surface(shape = RoundedCornerShape(5.dp), color = menuColor) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onCopy?.let {
                        CommentMenuButton("复制", it, "moments_comment_copy")
                    }
                    if (onCopy != null && onDelete != null) Box(Modifier.width(0.5.dp).height(18.dp).background(Color.White.copy(alpha = 0.25f)))
                    onDelete?.let {
                        CommentMenuButton("删除", it, "moments_comment_delete")
                    }
                }
            }
            if (!below) Arrow()
        }
    }
}

@Composable
private fun CommentMenuButton(text: String, onClick: () -> Unit, tag: String) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(0.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
        modifier = Modifier.widthIn(min = 72.dp).height(44.dp).testTag(tag),
    ) {
        Text(text, fontSize = 14.sp)
    }
}

/**
 * 删除评论的二次确认框；取消、外部点击和返回均不提交删除。
 *
 * @param onDismiss 关闭确认框时触发
 * @param onConfirm 点击红色“删除”时触发
 */
@Composable
internal fun MomentsCommentDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = momentsBackgroundColor(),
            modifier = Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("moments_comment_delete_dialog"),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "删除该评论？",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
                )
                HorizontalDivider(thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth()) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(0.dp), modifier = Modifier.weight(1f).height(50.dp).testTag("moments_comment_delete_cancel")) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp)
                    }
                    Box(Modifier.width(0.5.dp).height(50.dp).background(MaterialTheme.colorScheme.outlineVariant))
                    TextButton(onClick = onConfirm, shape = RoundedCornerShape(0.dp), modifier = Modifier.weight(1f).height(50.dp).testTag("moments_comment_delete_confirm")) {
                        Text("删除", color = Color(0xFFFA5151), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
