package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsNav
import plus.rua.project.ui.MomentsNetworkPermission
import plus.rua.project.ui.MomentsSearchScreen
import plus.rua.project.ui.theme.YaYaTheme

/** 个人朋友圈搜索壳 Activity，检索与界面均由 core 提供。 */
class MomentsSearchActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YaYaTheme {
                MomentsNetworkPermission(onBack = { finishWithSlideBack() }, content = {
                    MomentsSearchScreen(
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
