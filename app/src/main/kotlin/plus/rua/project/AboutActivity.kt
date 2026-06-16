package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.AboutScreen
import plus.rua.project.ui.theme.YaYaTheme

class AboutActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                AboutScreen(
                    onBack = { finishWithSlideBack() },
                    onNavigateToLicenses = {
                        startActivityWithSlide(Intent(this, LicensesActivity::class.java))
                    },
                    onNavigateToDogPark = {}
                )
            }
        }
    }
}
