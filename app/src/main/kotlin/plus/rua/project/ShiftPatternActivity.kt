package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.ShiftPatternScreen
import plus.rua.project.ui.theme.YaYaTheme

class ShiftPatternActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                ShiftPatternScreen(onBack = { finishWithSlideBack() })
            }
        }
    }
}
