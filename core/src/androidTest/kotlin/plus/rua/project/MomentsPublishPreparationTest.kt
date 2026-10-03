package plus.rua.project

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import plus.rua.project.ui.MomentsPublishScreen
import plus.rua.project.ui.theme.YaYaTheme
import java.io.File

/** 发布页图片处理状态的实际渲染和重试交互，不连接真实朋友圈账号。 */
@RunWith(AndroidJUnit4::class)
class MomentsPublishPreparationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preparingPhotos_showsProgressAndDisablesPublish() {
        show(MomentsPublishUiState(text = "保留的草稿", isPreparingPhotos = true, preparingPhotoIndex = 2, preparingPhotoCount = 9)) {}
        compose.onNodeWithText("正在处理图片 2/9").assertIsDisplayed()
        compose.onNodeWithText("保留的草稿").assertIsDisplayed()
        compose.onNodeWithTag("publish_submit_button").assertIsNotEnabled()
        screenshot("moments-image-preparing.png")
    }

    @Test fun failedPhoto_retryInvokesPreparationAndPreservesDraft() {
        var retries = 0
        show(MomentsPublishUiState(text = "保留的草稿", error = "图片压缩失败", errorSource = MomentsPublishErrorSource.Photo, failedPhotoIndex = 2)) { retries++ }
        compose.onNodeWithText("第 2 张图片未能添加").assertIsDisplayed()
        compose.onNodeWithText("保留的草稿").assertIsDisplayed()
        screenshot("moments-image-retry.png")
        compose.onNodeWithText("重试处理").performClick()
        assertEquals(1, retries)
    }

    @Test fun unsupportedPhoto_offersReselection() {
        show(MomentsPublishUiState(error = "暂不支持动态图，请选择静态图片", errorSource = MomentsPublishErrorSource.Photo, failedPhotoIndex = 1, canRetryPhotos = false)) {}
        compose.onNodeWithText("重新选图").assertIsDisplayed()
    }

    private fun show(initial: MomentsPublishUiState, retry: () -> Unit) {
        val state = mutableStateOf(initial)
        compose.setContent {
            YaYaTheme {
                MomentsPublishScreen(state.value, onCancel = {}, onPublish = {}, onTextChange = {}, onAddPhotosClick = {}, onRemovePhoto = {}, onLocationClick = {}, onVisibilityClick = {}, onRetryPhotos = retry)
            }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, name)
        UiDevice.getInstance(instrumentation).takeScreenshot(file)
        println("Screenshot: ${file.absolutePath}")
    }
}
