package plus.rua.project

import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.DateRecorderNav
import plus.rua.project.ui.RecordEditScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 记录编辑页 Activity。
 *
 * 两种入口：
 * - 新建：Intent extra [DateRecorderNav.EXTRA_TEMP_PHOTO_PATH] 携带照片路径
 * - 编辑：Intent extra [DateRecorderNav.EXTRA_RECORD_ID] 携带记录 ID
 */
class RecordEditActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val photoPath = intent?.getStringExtra(DateRecorderNav.EXTRA_TEMP_PHOTO_PATH)
        val recordId = intent?.getLongExtra(DateRecorderNav.EXTRA_RECORD_ID, -1L)
            ?.takeIf { it >= 0 }

        setContent {
            YaYaTheme {
                RecordEditScreen(
                    onBack = { finishWithSlideBack() },
                    photoPath = photoPath,
                    recordId = recordId
                )
            }
        }
    }
}
