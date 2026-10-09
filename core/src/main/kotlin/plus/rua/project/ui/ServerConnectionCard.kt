package plus.rua.project.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import plus.rua.project.ServerConnectionUiState
import plus.rua.project.ServerStatus
import java.net.URI

private data class StatusColors(
    val online: Color,
    val warning: Color,
)

@Composable
private fun statusColors(): StatusColors = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
    StatusColors(online = Color(0xFF81C995), warning = Color(0xFFFDD663))
} else {
    StatusColors(online = Color(0xFF2E7D32), warning = Color(0xFFE09B00))
}

/** 状态灯颜色：在线绿色、检查中与待授权琥珀色、无法连接为错误色。 */
@Composable
private fun ServerStatus.color(): Color {
    val colors = statusColors()
    return when (this) {
        is ServerStatus.Online -> colors.online
        is ServerStatus.Offline -> MaterialTheme.colorScheme.error
        ServerStatus.Checking, ServerStatus.NeedsLocalNetwork -> colors.warning
    }
}

private fun ServerStatus.label(): String = when (this) {
    ServerStatus.Checking -> "检查中"
    is ServerStatus.Online -> "已连接"
    is ServerStatus.Offline -> "无法连接"
    ServerStatus.NeedsLocalNetwork -> "待授权"
}

/** 卡片标题下展示主机名与端口，完整地址在编辑时查看。 */
private fun displayHost(url: String): String = runCatching {
    val uri = URI(url)
    if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
}.getOrNull() ?: url

/**
 * 工具页的服务器设置卡片：收起时显示当前地址、连接状态与可用功能，点击标题原地展开编辑。
 *
 * @param uiState 服务器地址与连接状态
 * @param onTest 点击「测试连接」或键盘完成键时以编辑中的地址触发
 * @param onSave 点击「保存」时以编辑中的地址触发；返回 true 表示已保存，卡片随后播放完成动画并收起
 * @param onDraftChange 编辑中的地址变化或关闭编辑时触发，调用方应清除上一次的测试结果
 * @param onRequestLocalNetwork 局域网地址待授权时点击「允许访问本地网络」触发
 * @param modifier 布局修饰符
 */
@Composable
internal fun ServerConnectionCard(
    uiState: ServerConnectionUiState,
    onTest: (String) -> Unit,
    onSave: (String) -> Boolean,
    onDraftChange: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(uiState.url) }
    var justSaved by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val currentDraftChange by rememberUpdatedState(onDraftChange)

    fun collapse() {
        focusManager.clearFocus()
        expanded = false
        currentDraftChange()
    }

    // 保存成功后先展示对勾，再收起卡片
    LaunchedEffect(uiState.savedCount) {
        if (uiState.savedCount == 0) return@LaunchedEffect
        justSaved = true
        delay(700)
        collapse()
        justSaved = false
    }

    // 展开或测试结果出现后，把整张卡片滚入可见区域，避免按钮藏在屏幕下方
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(expanded, uiState.draftStatus?.javaClass) {
        if (expanded) {
            delay(380)
            bringIntoView.bringIntoView()
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (expanded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "serverCardColor",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "serverChevron",
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).testTag("server_connection_card"),
    ) {
        Row(
            modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    if (expanded) {
                        collapse()
                    } else {
                        draft = uiState.url
                        expanded = true
                    }
                }.padding(20.dp)
                .testTag("server_connection_header"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "服务器",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    StatusPill(uiState.status)
                }
                AnimatedContent(
                    targetState = displayHost(uiState.url),
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "serverHost",
                ) { host ->
                    Text(
                        text = host,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "收起服务器设置" else "展开服务器设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(chevronRotation),
                    )
                }
            }
        }

        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                (fadeIn(tween(durationMillis = 220, delayMillis = 90)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220, delayMillis = 90)))
                    .togetherWith(fadeOut(tween(durationMillis = 90)))
                    .using(SizeTransform { _, _ -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow) })
            },
            label = "serverCardBody",
        ) { isExpanded ->
            if (isExpanded) {
                ServerEditor(
                    uiState = uiState,
                    draft = draft,
                    justSaved = justSaved,
                    onDraftChange = {
                        draft = it
                        onDraftChange()
                    },
                    onTest = {
                        focusManager.clearFocus()
                        onTest(draft)
                    },
                    onSave = {
                        focusManager.clearFocus()
                        if (draft.trim().trimEnd('/') == uiState.url) collapse() else onSave(draft)
                    },
                    onCancel = ::collapse,
                    onRequestLocalNetwork = onRequestLocalNetwork,
                )
            } else {
                ServerSummary(status = uiState.status, onRequestLocalNetwork = onRequestLocalNetwork)
            }
        }
    }
}

@Composable
private fun StatusPill(status: ServerStatus) {
    val color by animateColorAsState(targetValue = status.color(), label = "serverStatusColor")
    val pulse = rememberInfiniteTransition(label = "serverStatusPulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "serverStatusPulseAlpha",
    )
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .alpha(if (status is ServerStatus.Checking) pulseAlpha else 1f)
                    .background(color, CircleShape),
            )
            AnimatedContent(targetState = status.label(), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "serverStatusLabel") {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 收起时的正文：可用功能标签，或连接失败、待授权的说明。 */
@Composable
private fun ServerSummary(
    status: ServerStatus,
    onRequestLocalNetwork: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        ServerStatusDetail(status = status, onRequestLocalNetwork = onRequestLocalNetwork, showSuccessTitle = false)
    }
}

/**
 * 状态详情：在线时显示功能标签，失败时显示原因，待授权时提供授权按钮。
 *
 * @param showSuccessTitle 编辑区测试成功时额外显示「连接成功」
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerStatusDetail(
    status: ServerStatus,
    onRequestLocalNetwork: () -> Unit,
    showSuccessTitle: Boolean,
) {
    when (status) {
        ServerStatus.Checking -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在连接…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        is ServerStatus.Online -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showSuccessTitle) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = status.color(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("连接成功", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapabilityChip("朋友圈", status.moments)
                    CapabilityChip(if (status.period) "经期同步" else "经期同步需升级后端", status.period)
                    status.version?.let { VersionChip("v$it") }
                }
            }
        }

        is ServerStatus.Offline -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(status.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        ServerStatus.NeedsLocalNetwork -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "这是局域网里的服务器，需要允许访问本地网络后才能连接。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onRequestLocalNetwork) { Text("允许访问本地网络") }
            }
        }
    }
}

@Composable
private fun CapabilityChip(
    label: String,
    available: Boolean,
) {
    val tint = if (available) statusColors().online else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (available) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = if (available) "可用" else "不可用",
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VersionChip(label: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerEditor(
    uiState: ServerConnectionUiState,
    draft: String,
    justSaved: Boolean,
    onDraftChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onRequestLocalNetwork: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            "朋友圈与经期记录共用这个地址，保存后下次进入时生效。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            label = { Text("服务器地址") },
            placeholder = { Text("https://yaya.example.com") },
            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
            trailingIcon = {
                if (draft.isNotEmpty()) {
                    IconButton(onClick = { onDraftChange("") }) { Icon(Icons.Outlined.Cancel, contentDescription = "清空地址") }
                }
            },
            singleLine = true,
            isError = uiState.draftError != null,
            supportingText = { Text(uiState.draftError ?: "填写服务根地址，不含 /api/v1") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onTest() }),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("server_url_field"),
        )

        if (uiState.presets.size > 1 || uiState.presets.firstOrNull()?.url != draft) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("常用地址", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.presets.forEach { preset ->
                        FilterChip(
                            selected = draft.trim().trimEnd('/') == preset.url,
                            onClick = { onDraftChange(preset.url) },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("server_preset_${preset.label}"),
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = uiState.draftStatus,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically { it / 3 }) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            contentKey = { it?.javaClass },
            label = "serverDraftStatus",
        ) { status ->
            if (status == null) {
                Spacer(Modifier.fillMaxWidth())
            } else {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.padding(14.dp)) {
                        ServerStatusDetail(status = status, onRequestLocalNetwork = onRequestLocalNetwork, showSuccessTitle = true)
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("取消") }
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onTest,
                enabled = uiState.draftStatus !is ServerStatus.Checking,
                modifier = Modifier.testTag("server_test"),
            ) { Text("测试连接") }
            Spacer(Modifier.width(8.dp))
            // 保存成功后保持主色展示对勾，只忽略重复点击
            Button(onClick = { if (!justSaved) onSave() }, modifier = Modifier.testTag("server_save")) {
                AnimatedContent(
                    targetState = justSaved,
                    transitionSpec = { (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut() },
                    label = "serverSaveLabel",
                ) { saved ->
                    if (saved) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("已保存")
                        }
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}
