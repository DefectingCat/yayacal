package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.DateCheckerScreen
import plus.rua.project.ui.theme.YaYaTheme

class DateCheckerActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                DateCheckerScreen(
                onBack = { finishWithSlideBack() }
            )
            }
        }
    }
}
