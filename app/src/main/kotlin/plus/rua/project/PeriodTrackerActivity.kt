package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.PeriodTrackerScreen
import plus.rua.project.ui.theme.YaYaTheme

class PeriodTrackerActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                PeriodTrackerScreen(
                    onBack = { finishWithSlideBack() },
                    onOpenHistory = {
                        startActivityWithSlide(Intent(this, PeriodHistoryActivity::class.java))
                    },
                    onOpenSettings = {
                        startActivityWithSlide(Intent(this, PeriodSettingsActivity::class.java))
                    },
                )
            }
        }
    }
}
