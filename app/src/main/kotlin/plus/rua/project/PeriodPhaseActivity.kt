package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.PeriodPhaseScreen
import plus.rua.project.ui.theme.YaYaTheme

class PeriodPhaseActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                PeriodPhaseScreen(onBack = { finishWithSlideBack() })
            }
        }
    }
}
