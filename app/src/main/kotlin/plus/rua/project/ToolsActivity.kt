package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.ToolsScreen
import plus.rua.project.ui.theme.YaYaTheme

class ToolsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                ToolsScreen(
                onBack = { finishWithSlideBack() },
                onNavigateToDateChecker = {
                    startActivityWithSlide(Intent(this, DateCheckerActivity::class.java))
                }
            )
            }
        }
    }
}
