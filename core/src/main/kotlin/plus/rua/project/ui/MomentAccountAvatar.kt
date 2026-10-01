package plus.rua.project.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.github.panpf.sketch.AsyncImage
import plus.rua.project.MomentAccount

/**
 * 账号选择与切换动画共用的头像；远端图片加载中或失败时保留该账号内置头像。
 *
 * @param account 头像所属账号，用于默认图片和无障碍说明
 * @param avatarPath 服务端头像路径，为空时显示默认头像
 * @param modifier 布局修饰符，裁切与圆角由调用方决定
 */
@Composable
internal fun MomentAccountAvatar(account: MomentAccount, avatarPath: String?, modifier: Modifier = Modifier) {
    val uri = remember(avatarPath) { resolvePhotoUri(avatarPath) }
    Box(modifier) {
        Image(
            painter = painterResource(account.avatarResId),
            contentDescription = if (uri == null) "${account.name} 头像" else null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (uri != null) {
            AsyncImage(
                uri = uri,
                contentDescription = "${account.name} 头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
