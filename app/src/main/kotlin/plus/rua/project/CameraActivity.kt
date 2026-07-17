package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.CameraScreen
import plus.rua.project.ui.DateRecorderNav
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 相机拍摄页 Activity。
 *
 * 拍照成功后把临时照片路径透传给记录编辑页（M3 之后会先经过编辑器页）。
 */
class CameraActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            YaYaTheme {
                CameraScreen(
                    onBack = { finishWithSlideBack() },
                    onPhotoCaptured = { tempPath ->
                        // 拍照后先进入编辑器，编辑完成后再进入记录编辑页
                        startActivityWithSlide(
                            Intent(this, PhotoEditorActivity::class.java).apply {
                                putExtra(DateRecorderNav.EXTRA_TEMP_PHOTO_PATH, tempPath)
                            }
                        )
                        finishWithSlideBack()
                    }
                )
            }
        }
    }
}
