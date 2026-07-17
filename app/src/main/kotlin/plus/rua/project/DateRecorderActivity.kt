package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.DateRecorderNav
import plus.rua.project.ui.DateRecorderScreen
import plus.rua.project.ui.theme.YaYaTheme

class DateRecorderActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                DateRecorderScreen(
                    onBack = { finishWithSlideBack() },
                    onOpenCamera = {
                        startActivityWithSlide(Intent(this, CameraActivity::class.java))
                    },
                    onOpenRecord = { id ->
                        startActivityWithSlide(
                            Intent(this, RecordDetailActivity::class.java).apply {
                                putExtra(DateRecorderNav.EXTRA_RECORD_ID, id)
                            }
                        )
                    }
                )
            }
        }
    }
}
