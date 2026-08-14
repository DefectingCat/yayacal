package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.AnniversaryDatesScreen
import plus.rua.project.ui.theme.YaYaTheme

/** 纪念日期页面壳 Activity，仅承载 Compose 内容。 */
class AnniversaryDatesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                AnniversaryDatesScreen(
                    onBack = { finishWithSlideBack() },
                )
            }
        }
    }
}
