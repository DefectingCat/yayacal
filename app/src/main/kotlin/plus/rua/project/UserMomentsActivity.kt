package plus.rua.project

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
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
                    onBack = { finishWithSlideBack() },
                    onPublish = {
                        Toast.makeText(this, "发表动态功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                    onPrivatePublish = {
                        Toast.makeText(this, "私密发表功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                    onSearch = {
                        Toast.makeText(this, "搜索朋友圈功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                    onViewModeChange = {
                        Toast.makeText(this, "相册视图切换功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                    onNotifications = {
                        Toast.makeText(this, "消息列表功能正在开发中", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
