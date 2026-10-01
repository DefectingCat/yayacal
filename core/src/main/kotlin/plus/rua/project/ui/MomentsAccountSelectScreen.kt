package plus.rua.project.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import plus.rua.project.MomentAccount

/**
 * 朋友圈账号选择页面，极简风格，居中竖排展示可选账号列表。
 * 头像参考微信头像规范，采用方形微圆角设计，支持点击进入朋友圈并触发头像位移动画。
 *
 * @param accounts 可选账号列表，默认为小白与小鸡毛
 * @param currentAccountId 当前已选中的账号 ID，用于展示状态角标
 * @param animatingAccountId 正在播放位移动画的账号 ID，动画期间隐藏该项静态头像
 * @param onAccountClick 点击某个账号时触发，传入选中的 [MomentAccount]
 * @param onAccountPositioned 账号头像完成布局定位后触发，传递其在根视图中的坐标矩形
 * @param avatarPaths 按账号 ID 提供的头像路径；未获取资料时使用该账号内置头像
 * @param onBack 点击顶部返回按钮时触发
 * @param modifier 外部布局修饰符
 */
@Composable
fun MomentsAccountSelectScreen(
    currentAccountId: String?,
    onAccountClick: (MomentAccount) -> Unit,
    onBack: () -> Unit,
    accounts: List<MomentAccount> = MomentAccount.ALL_ACCOUNTS,
    animatingAccountId: String? = null,
    onAccountPositioned: (account: MomentAccount, bounds: Rect) -> Unit = { _, _ -> },
    avatarPaths: Map<String, String?> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .testTag("moments_account_select_screen"),
    ) {
        // 顶部返回按钮与状态栏安全区
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("moments_account_select_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // 屏幕正中央：简约竖排账号选择
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
        ) {
            // 标题与副标题
            Text(
                text = "选择账号",
                style =
                MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "轻触头像进入朋友圈",
                style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    letterSpacing = 0.2.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 竖排账号卡片项
            accounts.forEachIndexed { index, account ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(36.dp))
                }

                AccountItem(
                    account = account,
                    avatarPath = avatarPaths[account.id],
                    isCurrent = account.id == currentAccountId,
                    isAnimating = account.id == animatingAccountId,
                    onClick = { onAccountClick(account) },
                    onPositioned = { rect -> onAccountPositioned(account, rect) },
                )
            }
        }
    }
}

/**
 * 单个账号展示项，微信风格方形微圆角头像 + 底部昵称与状态。
 */
@Composable
private fun AccountItem(
    account: MomentAccount,
    avatarPath: String?,
    isCurrent: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "account_press_scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
        Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .testTag("moments_account_item_${account.id}"),
    ) {
        // 方形头像框（微信标准：1:1 比例，RoundedCornerShape 12.dp，精致细边线与柔和阴影）
        Box(
            modifier =
            Modifier
                .size(82.dp)
                .onGloballyPositioned { coordinates ->
                    onPositioned(coordinates.boundsInRoot())
                },
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border =
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                ),
                shadowElevation = 3.dp,
                modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .alpha(if (isAnimating) 0f else 1f),
            ) {
                MomentAccountAvatar(
                    account = account,
                    avatarPath = avatarPath,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 当前使用账号角标（微信绿小对勾）
            if (isCurrent && !isAnimating) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF07C160),
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier =
                    Modifier
                        .size(20.dp)
                        .align(Alignment.BottomEnd),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "当前使用",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 账号昵称
        Text(
            text = account.name,
            style =
            MaterialTheme.typography.titleMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )

        // 账号描述或当前标签
        if (isCurrent) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "当前使用",
                style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color(0xFF07C160),
            )
        }
    }
}
