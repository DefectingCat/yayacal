package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.PeriodHistoryScreen
import plus.rua.project.ui.theme.YaYaTheme

class PeriodHistoryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                PeriodHistoryScreen(onBack = { finishWithSlideBack() })
            }
        }
    }
}
