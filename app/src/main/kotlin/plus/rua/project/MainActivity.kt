package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.ReportDrawn
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import plus.rua.project.ui.BirthdayGreetingOverlay
import plus.rua.project.ui.CalendarMonthView
import plus.rua.project.ui.ConfessionGreetingOverlay
import plus.rua.project.ui.QixiGreetingOverlay
import plus.rua.project.ui.theme.YaYaTheme

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                // 在主题内的首次 composition 绘制完成后报告 fully drawn，用于准确的启动时间度量。
                ReportDrawn()
                Box {
                    CalendarMonthView(
                        onNavigateToAbout = {
                            startActivityWithSlide(Intent(this@MainActivity, AboutActivity::class.java))
                        },
                        onNavigateToTools = {
                            startActivityWithSlide(Intent(this@MainActivity, ToolsActivity::class.java))
                        },
                        onNavigateToShiftSettings = {
                            startActivityWithSlide(Intent(this@MainActivity, ShiftPatternActivity::class.java))
                        },
                    )
                    // 七夕当天冷启动盖全屏问候层，其余日期不渲染
                    QixiGreetingOverlay()
                    // 生日（公历 9 月 4 日）当天冷启动盖全屏问候层，其余日期不渲染
                    BirthdayGreetingOverlay()
                    // 表白日（公历 11 月 4 日）当天冷启动盖全屏问候层，其余日期不渲染
                    ConfessionGreetingOverlay()
                }
            }
        }
    }
}
