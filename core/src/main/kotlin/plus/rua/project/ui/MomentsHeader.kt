package plus.rua.project.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.panpf.sketch.AsyncImage
import java.io.File

/**
 * 朋友圈与个人相册共用的头部组件，包含全宽相册封面、右下角用户名与跨界重叠头像，
 * 并支持点击封面放大展开动画与“换封面”操作。
 *
 * @param username 用户名
 * @param avatarPath 头像本地文件路径（为空时展示占位符）
 * @param coverPath 封面本地文件路径（为空时展示占位符）
 * @param isCoverExpanded 封面是否处于点击放大展开状态
 * @param onCoverClick 点击封面触发（展开或折叠）
 * @param onChangeCoverClick 点击“换封面”按钮时触发
 * @param isAvatarVisible 头像是否可见（用于头像入场位移动画期间临时隐藏）
 * @param onAvatarPositioned 头像在根坐标系中完成布局时的回调，传递其 Rect 坐标
 * @param onAvatarClick 点击头像时触发
 * @param onAvatarLongClick 长按头像时触发（可选）
 * @param canEdit 是否显示更换封面的入口
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MomentsHeader(
    username: String,
    avatarPath: String?,
    coverPath: String?,
    isCoverExpanded: Boolean,
    onCoverClick: () -> Unit,
    onChangeCoverClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onAvatarLongClick: (() -> Unit)? = null,
    isAvatarVisible: Boolean = true,
    onAvatarPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    canEdit: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val normalCoverHeight = 300.dp
    val expandedCoverHeight = 520.dp
    val avatarSize = 74.dp
    val avatarOverlapBelow = 34.dp

    val animatedCoverHeight by animateDpAsState(
        targetValue = if (isCoverExpanded && canEdit) expandedCoverHeight else normalCoverHeight,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "cover_height",
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isCoverExpanded && canEdit) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "content_alpha",
    )
    val changeCoverBtnAlpha by animateFloatAsState(
        targetValue = if (isCoverExpanded && canEdit) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "change_btn_alpha",
    )

    val totalHeight = animatedCoverHeight + if (isCoverExpanded && canEdit) 0.dp else avatarOverlapBelow

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(totalHeight),
    ) {
        // 1. 头图 / 相册封面区域
        Box(
            modifier =
            Modifier
                .fillMaxWidth()
                .height(animatedCoverHeight)
                .align(Alignment.TopCenter)
                .background(
                    brush =
                    Brush.verticalGradient(
                        colors =
                        listOf(
                            Color(0xFF263238),
                            Color(0xFF1E272C),
                            Color(0xFF12161A),
                        ),
                    ),
                )
                .clickable(onClick = onCoverClick)
                .testTag("moments_cover"),
            contentAlignment = Alignment.Center,
        ) {
            val coverUri = remember(coverPath) { resolvePhotoUri(coverPath) }

            if (coverUri != null) {
                AsyncImage(
                    uri = coverUri,
                    state = rememberMomentsImageState(),
                    contentDescription = "相册封面",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.15f),
                        modifier = Modifier.size(54.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                contentDescription = "相册封面占位",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Text(
                        text = if (canEdit) "轻触更换相册封面" else "暂无相册封面",
                        style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        color = Color.White.copy(alpha = 0.75f),
                    )
                }
            }

            // 展开时右下角的“换封面”按钮
            if (changeCoverBtnAlpha > 0.05f) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 24.dp)
                        .alpha(changeCoverBtnAlpha)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onChangeCoverClick)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("moments_change_cover_button"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = "换封面",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "换封面",
                        style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            shadow =
                            Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(1f, 1f),
                                blurRadius = 4f,
                            ),
                        ),
                        color = Color.White,
                    )
                }
            }
        }

        // 2. 用户名：位于封面底部上方、头像左侧（展开时淡出隐藏）
        if (contentAlpha > 0.05f) {
            Text(
                text = username,
                color = Color.White,
                style =
                MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    shadow =
                    Shadow(
                        color = Color.Black.copy(alpha = 0.7f),
                        offset = Offset(1f, 2f),
                        blurRadius = 6f,
                    ),
                ),
                maxLines = 1,
                modifier =
                Modifier
                    .padding(top = normalCoverHeight - 28.dp)
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp + avatarSize + 16.dp)
                    .alpha(contentAlpha)
                    .testTag("moments_username"),
            )

            // 3. 用户头像：右下角跨界悬浮（展开时淡出隐藏）
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, Color.White),
                shadowElevation = 2.dp,
                modifier =
                Modifier
                    .padding(top = normalCoverHeight - 40.dp)
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp)
                    .size(avatarSize)
                    .clip(RoundedCornerShape(10.dp))
                    .alpha(if (isAvatarVisible) contentAlpha else 0f)
                    .onGloballyPositioned { coordinates ->
                        onAvatarPositioned?.invoke(coordinates.boundsInRoot())
                    }
                    .combinedClickable(
                        onClick = onAvatarClick,
                        onLongClick = onAvatarLongClick,
                    )
                    .testTag("moments_avatar"),
            ) {
                val avatarUri = remember(avatarPath) { resolvePhotoUri(avatarPath) }

                if (avatarUri != null) {
                    AsyncImage(
                        uri = avatarUri,
                        state = rememberMomentsImageState(avatar = true),
                        contentDescription = "用户头像",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "头像占位",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
            }
        }
    }
}
