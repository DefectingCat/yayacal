package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsAlbumScreen
import plus.rua.project.ui.MomentsNav
import plus.rua.project.ui.MomentsNetworkPermission
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 朋友圈相册视图壳 Activity，仅承载 Compose 内容。
 */
class MomentsAlbumActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                MomentsNetworkPermission(onBack = { finishWithSlideBack() }, content = {
                    MomentsAlbumScreen(
                        authorId = intent.getStringExtra(MomentsNav.EXTRA_AUTHOR_ID),
                        onBack = { finishWithSlideBack() },
                        onPostClick = { postId ->
                            startActivityWithSlide(
                                Intent(this, MomentsDetailActivity::class.java).apply {
                                    putExtra(MomentsNav.EXTRA_POST_ID, postId)
                                },
                            )
                        },
                    )
                })
            }
        }
    }
}
