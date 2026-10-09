package plus.rua.project.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import plus.rua.project.PeriodSettings
import plus.rua.project.PeriodViewModel

/**
 * 经期设置页（有状态版本）。
 *
 * @param onBack 点击左上角返回时触发
 * @param viewModel 经期页面共用的 ViewModel，默认按应用 Context 创建
 * @param modifier 布局修饰符
 */
@Composable
fun PeriodSettingsScreen(
    onBack: () -> Unit,
    viewModel: PeriodViewModel = periodViewModel(),
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    PeriodSettingsScreen(
        settings = uiState.document.settings,
        message = message,
        onBack = onBack,
        onUpdateSettings = viewModel::updateSettings,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

/**
 * 经期设置页（无状态版本）：预测用的默认周期、经期与黄体期长度，修改随文档同步到两台手机。
 *
 * @param settings 当前设置
 * @param message 一次性提示，显示为 Snackbar
 * @param onBack 点击左上角返回时触发
 * @param onUpdateSettings 每次点击加减按钮时以新设置触发
 * @param onDismissMessage [message] 显示完毕后触发
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSettingsScreen(
    settings: PeriodSettings,
    message: String?,
    onBack: () -> Unit,
    onUpdateSettings: (PeriodSettings) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onDismissMessage()
        }
    }
    Scaffold(
        modifier = modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = { Text("经期设置", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ChevronLeft, contentDescription = "返回") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "预测默认值",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp),
            )
            Card(
                shape = PeriodCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    SettingStepper(
                        title = "默认周期长度",
                        description = "两次经期开始日的间隔",
                        value = settings.cycleLength,
                        range = PeriodSettings.CYCLE_RANGE,
                        onChange = { onUpdateSettings(settings.copy(cycleLength = it)) },
                        tag = "cycle",
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    SettingStepper(
                        title = "默认经期长度",
                        description = "每次经期持续的天数",
                        value = settings.periodLength,
                        range = PeriodSettings.PERIOD_RANGE,
                        onChange = { onUpdateSettings(settings.copy(periodLength = it)) },
                        tag = "period",
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp))
                    SettingStepper(
                        title = "黄体期长度",
                        description = "排卵日到下次经期的天数，用于推算易孕期",
                        value = settings.lutealLength,
                        range = PeriodSettings.LUTEAL_RANGE,
                        onChange = { onUpdateSettings(settings.copy(lutealLength = it)) },
                        tag = "luteal",
                    )
                }
            }
            Text(
                text = "记录不足 2 个周期时，按这里的默认周期和经期长度预测；记录足够后改用最近记录的中位数。黄体期长度始终用于推算排卵日。设置会同步到两台手机。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Text(
                text = "预测基于历史记录，仅供参考，不能作为避孕或医疗依据。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingStepper(
    title: String,
    description: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    tag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > range.first,
            modifier = Modifier.testTag("period_setting_${tag}_minus"),
        ) { Icon(Icons.Filled.Remove, contentDescription = "减少$title") }
        Text(
            text = "$value 天",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            enabled = value < range.last,
            modifier = Modifier.testTag("period_setting_${tag}_plus"),
        ) { Icon(Icons.Filled.Add, contentDescription = "增加$title") }
    }
}
