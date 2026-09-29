package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsDetailScreen
import plus.rua.project.ui.MomentsNav
import plus.rua.project.ui.theme.YaYaTheme

/** 朋友圈详情页壳 Activity，仅承载 Compose 内容与导航参数。 */
class MomentsDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val postId = intent.getStringExtra(MomentsNav.EXTRA_POST_ID) ?: run {
            finish()
            return
        }
        setContent {
            YaYaTheme {
                MomentsDetailScreen(
                    postId = postId,
                    focusComment = intent.getBooleanExtra(MomentsNav.EXTRA_FOCUS_COMMENT, false),
                    onBack = { finishWithSlideBack() },
                )
            }
        }
    }
}
