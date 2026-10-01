package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.MomentsNetworkPermission
import plus.rua.project.ui.MomentsPublishScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 朋友圈发布动态页面壳 Activity，仅承载 Compose 内容。
 */
class MomentsPublishActivity : BaseActivity() {

    companion object {
        const val EXTRA_VISIBILITY = "extra_visibility"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val visibility = intent.getStringExtra(EXTRA_VISIBILITY) ?: "公开"

        setContent {
            YaYaTheme {
                MomentsNetworkPermission(onBack = { finishWithSlideBack() }, content = {
                    MomentsPublishScreen(
                        initialVisibility = visibility,
                        onCancel = { finishWithSlideBack() },
                        onPublishedSuccess = { finishWithSlideBack() },
                    )
                })
            }
        }
    }
}
