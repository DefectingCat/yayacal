package plus.rua.project

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsNav
import plus.rua.project.ui.UserMomentsScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 个人朋友圈相册视图壳 Activity，仅承载 Compose 内容。
 */
class UserMomentsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                UserMomentsScreen(
                    authorId = intent.getStringExtra(MomentsNav.EXTRA_AUTHOR_ID),
                    onBack = { finishWithSlideBack() },
                    onPublish = {
                        val intent = Intent(this, MomentsPublishActivity::class.java).apply {
                            putExtra(MomentsPublishActivity.EXTRA_VISIBILITY, "公开")
                        }
                        startActivityWithSlide(intent)
                    },
                    onPrivatePublish = {
                        val intent = Intent(this, MomentsPublishActivity::class.java).apply {
                            putExtra(MomentsPublishActivity.EXTRA_VISIBILITY, "私密")
                        }
                        startActivityWithSlide(intent)
                    },
                    onPostClick = { postId ->
                        startActivityWithSlide(
                            Intent(this, MomentsDetailActivity::class.java).apply {
                                putExtra(MomentsNav.EXTRA_POST_ID, postId)
                            },
                        )
                    },
                    onSearch = {
                        startActivityWithSlide(Intent(this, MomentsSearchActivity::class.java).apply { putExtra(MomentsNav.EXTRA_AUTHOR_ID, this@UserMomentsActivity.intent.getStringExtra(MomentsNav.EXTRA_AUTHOR_ID)) })
                    },
                    onViewModeChange = {
                        startActivityWithSlide(Intent(this, MomentsAlbumActivity::class.java).apply { putExtra(MomentsNav.EXTRA_AUTHOR_ID, this@UserMomentsActivity.intent.getStringExtra(MomentsNav.EXTRA_AUTHOR_ID)) })
                    },
                    onNotifications = {
                        startActivityWithSlide(Intent(this, MomentsNotificationsActivity::class.java))
                    },
                )
            }
        }
    }
}
