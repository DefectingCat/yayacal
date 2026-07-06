package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import plus.rua.project.ui.CalendarMonthView
import plus.rua.project.ui.theme.YaYaTheme

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                // 在主题内的首次 composition 绘制完成后报告 fully drawn，用于准确的启动时间度量。
                ReportDrawn()
                CalendarMonthView(
                    onNavigateToAbout = {
                        startActivityWithSlide(Intent(this, AboutActivity::class.java))
                    },
                    onNavigateToTools = {
                        startActivityWithSlide(Intent(this, ToolsActivity::class.java))
                    },
                    onNavigateToShiftSettings = {
                        startActivityWithSlide(Intent(this, ShiftPatternActivity::class.java))
                    }
                )
            }
        }
    }
}
