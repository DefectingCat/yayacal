package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.StaffPracticeScreen
import plus.rua.project.ui.theme.YaYaTheme

class StaffPracticeActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                StaffPracticeScreen(
                    onBack = { finishWithSlideBack() },
                )
            }
        }
    }
}
