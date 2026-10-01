package plus.rua.project

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsNav
import plus.rua.project.ui.MomentsNetworkPermission
import plus.rua.project.ui.MomentsNotificationsScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 朋友圈全部互动消息列表壳 Activity，仅承载 Compose 内容。
 */
class MomentsNotificationsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                MomentsNetworkPermission(onBack = { finishWithSlideBack() }, content = {
                    MomentsNotificationsScreen(
                        onBack = { finishWithSlideBack() },
                        onPostClick = { postId ->
                            startActivityWithSlide(Intent(this, MomentsDetailActivity::class.java).apply { putExtra(MomentsNav.EXTRA_POST_ID, postId) })
                        },
                    )
                })
            }
        }
    }
}
