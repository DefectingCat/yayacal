package plus.rua.project.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import plus.rua.project.MomentsConnection
import plus.rua.project.momentsServiceNeedsLocalNetwork

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * 朋友圈各 Activity 共用的本地网络授权入口，在授权完成前不创建联网页面及其 ViewModel。
 * 首次访问本地服务时申请权限；从系统设置返回后重新检查，公网和回环服务直接放行。
 * 拒绝权限后仍可修改服务地址，保存时重建 Activity，避免沿用旧连接的 ViewModel。
 *
 * @param onBack 在授权说明页点击返回时触发
 * @param content 地址不需要本地授权或权限已授予时展示的页面
 * @param modifier 授权说明和地址检查页面的布局修饰符
 */
@Composable
fun MomentsNetworkPermission(
    onBack: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var url by remember(context) { mutableStateOf(MomentsConnection.url(context)) }
    var granted by remember(context) { mutableStateOf(context.hasLocalNetworkPermission()) }
    var required by remember(url) { mutableStateOf<Boolean?>(null) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        denied = !it
        pending = false
    }

    DisposableEffect(context) {
        val preferences = context.getSharedPreferences(MomentsConnection.PREFS, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> url = MomentsConnection.url(context) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = context.hasLocalNetworkPermission()
        url = MomentsConnection.url(context)
    }
    LaunchedEffect(url) {
        required = withContext(Dispatchers.IO) { momentsServiceNeedsLocalNetwork(url, Build.VERSION.SDK_INT) }
    }
    LaunchedEffect(required, granted) {
        if (required == true && !granted && !requested) {
            requested = true
            pending = true
            launcher.launch(LOCAL_NETWORK_PERMISSION)
        }
    }

    if (showConnection) {
        MomentsConnectionDialog(
            onSaved = {
                showConnection = false
                activity?.recreate()
            },
            onDismiss = { showConnection = false },
        )
    }

    if (granted || required == false) {
        content()
        return
    }

    val openSettings = denied && activity?.shouldShowRequestPermissionRationale(LOCAL_NETWORK_PERMISSION) == false
    Surface(modifier.fillMaxSize()) {
        if (required == null) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                modifier = Modifier.padding(32.dp).testTag("moments_local_network_permission"),
            ) {
                Text("允许访问本地朋友圈", style = MaterialTheme.typography.titleLarge)
                Text("朋友圈服务位于本地网络，需要允许访问附近设备才能加载和发布动态。", textAlign = TextAlign.Center)
                Button(
                    enabled = !pending,
                    onClick = {
                        if (openSettings) {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                        } else {
                            requested = true
                            pending = true
                            launcher.launch(LOCAL_NETWORK_PERMISSION)
                        }
                    },
                ) { Text(if (openSettings) "打开应用设置" else "允许访问") }
                TextButton(onClick = { showConnection = true }) { Text("连接设置") }
                TextButton(onClick = onBack) { Text("返回") }
            }
        }
    }
}

private fun Context.hasLocalNetworkPermission(): Boolean = Build.VERSION.SDK_INT < 37 || ContextCompat.checkSelfPermission(this, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
