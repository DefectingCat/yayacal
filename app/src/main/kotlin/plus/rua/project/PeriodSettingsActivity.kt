package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.PeriodSettingsScreen
import plus.rua.project.ui.theme.YaYaTheme

class PeriodSettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                PeriodSettingsScreen(onBack = { finishWithSlideBack() })
            }
        }
    }
}
