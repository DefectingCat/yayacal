@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package plus.rua.project.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import plus.rua.project.momentsErrorDescription

/** 普通导航栏下的刷新容器；空列表同样保留完整手势区域，仅在允许且未刷新时调用 onRefresh。 */
@Composable
internal fun MomentsRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { if (enabled && !isRefreshing) onRefresh() },
        state = state,
        indicator = {
            MomentsRefreshIndicator(
                isRefreshing = isRefreshing,
                state = state,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                belowTopBar = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
        modifier = modifier,
    )
}

/** 统一的空状态、错误或权限说明；只有点击可选操作按钮时才调用 onAction。 */
@Composable
internal fun MomentsStateContent(
    title: String,
    description: String,
    isError: Boolean = false,
    seed: Any? = null,
    showPullHint: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 300.dp).padding(horizontal = 32.dp, vertical = 36.dp)
            .testTag(if (isError) "moments_error_placeholder" else "moments_empty_placeholder"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isError) {
            Box(
                Modifier.size(140.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), modifier = Modifier.size(64.dp))
            }
        } else {
            AnimatedWebp(seed = seed ?: title, modifier = Modifier.size(140.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(description, fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (showPullHint) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.ArrowDownward, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Text("下拉页面重试", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onAction, shape = CircleShape, modifier = Modifier.testTag("moments_state_action")) { Text(actionLabel) }
        }
    }
}

/** 首次请求失败；错误说明使用用户可理解的文案，重试交给页面下拉手势。 */
@Composable
internal fun MomentsErrorContent(error: String, title: String = "暂时连接不上", modifier: Modifier = Modifier) {
    MomentsStateContent(title = title, description = momentsErrorDescription(error), isError = true, showPullHint = true, modifier = modifier)
}

/** 已有内容或操作失败时的局部提示；可选按钮仅在点击时调用 onAction，避免自动重复写操作。 */
@Composable
internal fun MomentsErrorNotice(
    error: String,
    title: String = "暂时无法更新",
    hint: String? = "已保留当前内容，下拉页面重试",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth().testTag("moments_error_notice"), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f), modifier = Modifier.padding(top = 2.dp).size(26.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text(momentsErrorDescription(error), fontSize = 13.sp, lineHeight = 19.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                hint?.let { Text(it, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(onClick = onAction, shape = CircleShape, modifier = Modifier.padding(top = 8.dp)) { Text(actionLabel, fontSize = 13.sp) }
                }
            }
        }
    }
}

/** 列表末尾的分页状态；点击加载或重试时调用 onLoadMore，分页期间不显示顶部刷新进度。 */
@Composable
internal fun MomentsPagingFooter(
    hasMore: Boolean,
    isLoading: Boolean,
    error: String?,
    onLoadMore: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        when {
            isLoading -> MomentsLoadingSpinner(modifier = Modifier.padding(20.dp))
            error != null -> MomentsErrorNotice(error, title = "后面的内容暂时没加载出来", hint = "已加载的内容仍可查看", actionLabel = "重新加载", onAction = if (enabled) onLoadMore else null, modifier = Modifier.padding(16.dp))
            hasMore -> TextButton(onClick = onLoadMore, enabled = enabled) { Text("加载更多") }
        }
    }
}
