package plus.rua.project

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import plus.rua.project.ui.DateRecorderNav
import plus.rua.project.ui.RecordDetailScreen
import plus.rua.project.ui.theme.YaYaTheme

/**
 * 记录详情页 Activity。
 *
 * 接收记录 ID（[DateRecorderNav.EXTRA_RECORD_ID]），
 * 提供"编辑信息"（→ RecordEditActivity）与"编辑照片"（→ PhotoEditorActivity）入口。
 */
class RecordDetailActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val recordId = intent?.getLongExtra(DateRecorderNav.EXTRA_RECORD_ID, -1L)
            ?.takeIf { it >= 0 }
        requireNotNull(recordId) { "RecordDetailActivity 必须接收 EXTRA_RECORD_ID" }

        setContent {
            YaYaTheme {
                RecordDetailScreen(
                    onBack = { finishWithSlideBack() },
                    recordId = recordId,
                    onEditInfo = { id ->
                        startActivityWithSlide(
                            Intent(this, RecordEditActivity::class.java).apply {
                                putExtra(DateRecorderNav.EXTRA_RECORD_ID, id)
                            }
                        )
                    },
                    onEditPhoto = { photoPath ->
                        startActivityWithSlide(
                            Intent(this, PhotoEditorActivity::class.java).apply {
                                putExtra(DateRecorderNav.EXTRA_TEMP_PHOTO_PATH, photoPath)
                            }
                        )
                    }
                )
            }
        }
    }
}
