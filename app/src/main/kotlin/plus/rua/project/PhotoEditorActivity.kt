package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.DateRecorderNav
import plus.rua.project.ui.PhotoEditorScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 照片编辑页 Activity。
 *
 * 接收源照片路径（[DateRecorderNav.EXTRA_TEMP_PHOTO_PATH]，来自相机或详情页），
 * 编辑完成后跳转记录编辑页（携带最终照片路径）。
 */
class PhotoEditorActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourcePath = intent?.getStringExtra(DateRecorderNav.EXTRA_TEMP_PHOTO_PATH)
        val recordId = intent?.getLongExtra(DateRecorderNav.EXTRA_RECORD_ID, -1L)
            ?.takeIf { it >= 0 }
        requireNotNull(sourcePath) { "PhotoEditorActivity 必须接收 EXTRA_TEMP_PHOTO_PATH" }

        setContent {
            YaYaTheme {
                PhotoEditorScreen(
                    onBack = { finishWithSlideBack() },
                    onSaved = { finalPath ->
                        startActivityWithSlide(
                            Intent(this, RecordEditActivity::class.java).apply {
                                putExtra(DateRecorderNav.EXTRA_TEMP_PHOTO_PATH, finalPath)
                                if (recordId != null) {
                                    putExtra(DateRecorderNav.EXTRA_RECORD_ID, recordId)
                                }
                            }
                        )
                        finishWithSlideBack()
                    },
                    sourcePath = sourcePath
                )
            }
        }
    }
}
