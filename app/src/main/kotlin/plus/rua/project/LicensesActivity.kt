package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.LicensesScreen
import plus.rua.project.ui.theme.YaYaTheme

class LicensesActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                LicensesScreen(
                onBack = { finishWithSlideBack() }
            )
            }
        }
    }
}
