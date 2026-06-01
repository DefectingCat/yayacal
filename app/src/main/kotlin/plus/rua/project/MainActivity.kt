package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.CalendarMonthView
import plus.rua.project.ui.theme.YaYaTheme

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                CalendarMonthView(
                onNavigateToAbout = {
                    startActivityWithSlide(Intent(this, AboutActivity::class.java))
                },
                onNavigateToTools = {
                    startActivityWithSlide(Intent(this, ToolsActivity::class.java))
                }
            )
            }
        }
    }
}
