package plus.rua.project

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
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
                    onPublish = {
                        Toast.makeText(this, "发布动态功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                    onCoverClick = {
                        Toast.makeText(this, "更换相册封面功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                    onAvatarClick = {
                        Toast.makeText(this, "查看头像功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
