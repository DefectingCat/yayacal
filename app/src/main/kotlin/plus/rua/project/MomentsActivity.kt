package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsNav
import plus.rua.project.ui.MomentsScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 朋友圈页面壳 Activity，仅承载 Compose 内容。
 */
class MomentsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                MomentsScreen(
                    onBack = { finishWithSlideBack() },
                    onAvatarClick = {
                        startActivityWithSlide(Intent(this, UserMomentsActivity::class.java))
                    },
                    onPublish = {
                        startActivityWithSlide(Intent(this, MomentsPublishActivity::class.java))
                    },
                    onAuthorClick = { authorId ->
                        startActivityWithSlide(Intent(this, UserMomentsActivity::class.java).apply { putExtra(MomentsNav.EXTRA_AUTHOR_ID, authorId) })
                    },
                    onPostClick = { postId ->
                        startActivityWithSlide(
                            Intent(this, MomentsDetailActivity::class.java).apply {
                                putExtra(MomentsNav.EXTRA_POST_ID, postId)
                            },
                        )
                    },
                    onCommentClick = { postId ->
                        startActivityWithSlide(
                            Intent(this, MomentsDetailActivity::class.java).apply {
                                putExtra(MomentsNav.EXTRA_POST_ID, postId)
                                putExtra(MomentsNav.EXTRA_FOCUS_COMMENT, true)
                            },
                        )
                    },
                )
            }
        }
    }
}
