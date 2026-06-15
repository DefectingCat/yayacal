package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import plus.rua.project.ui.CalendarMonthView
import plus.rua.project.ui.theme.YaYaTheme

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            // 在首次 composition 绘制完成后报告 fully drawn，用于准确的启动时间度量。
            ReportDrawn()
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
