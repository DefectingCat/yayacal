package plus.rua.project

import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import plus.rua.project.ui.MomentsDetailScreen
import plus.rua.project.ui.theme.YaYaTheme
import java.io.File
import kotlin.math.roundToInt
import kotlin.time.Instant

/** 在独立测试 APK 中用内存动态验证真实触摸手势，测试不会操作用户的朋友圈数据。 */
@RunWith(AndroidJUnit4::class)
class MomentsCommentInteractionTest {
    @get:Rule
    val compose = createComposeRule()

    private val account = mutableStateOf<String?>("xiaobai")
    private val operationError = mutableStateOf<String?>(null)
    private val post = mutableStateOf(MomentPost())
    private val deleteRequests = mutableListOf<String>()
    private var failDelete = false

    @Before
    fun showDetail() {
        val timestamp = Instant.parse("2026-10-02T04:00:00Z").toEpochMilliseconds()
        post.value = MomentPost(
            id = "interaction-fixture",
            authorId = "xiaobai",
            authorName = "小白",
            timestamp = timestamp,
            text = "评论交互测试",
            likes = listOf(MomentPerson("xiaobai", "小白"), MomentPerson("xiaohuang", "小黄")),
            comments = listOf(
                MomentComment(id = "own-text", authorId = "xiaobai", authorName = "小白", text = "😀😀", timestamp = timestamp),
                MomentComment(id = "own-image", authorId = "xiaobai", authorName = "小白", text = "", photoPath = "file:///android_asset/animations/001.webp", timestamp = timestamp),
                MomentComment(id = "other-text", authorId = "xiaohuang", authorName = "小黄", text = "朋友的评论", timestamp = timestamp),
            ),
        )
        compose.setContent {
            YaYaTheme(darkTheme = false) {
                MomentsDetailScreen(
                    post = post.value,
                    currentAccountId = account.value,
                    onBack = {},
                    onLike = {},
                    onSendComment = { _, _, _ -> true },
                    onDeleteComment = { id ->
                        deleteRequests += id
                        if (failDelete) {
                            operationError.value = "删除失败，请稍后重试"
                        } else {
                            // 服务端保留删除标记，详情页应把该条从评论列表隐藏。
                            post.value = post.value.copy(comments = post.value.comments.map { if (it.id == id) it.copy(deleted = true) else it })
                        }
                    },
                    networkState = MomentsUiState(operationError = operationError.value),
                )
            }
        }
    }

    @Test
    fun longPressText_cancelConfirmation_doesNotDeleteOrReply() {
        compose.onNodeWithText("删除评论").assertDoesNotExist()
        saveScreenshot("comments-normal")
        openTextMenu()
        compose.onNodeWithTag("moments_comment_copy").assertIsDisplayed()
        compose.onNodeWithTag("moments_comment_delete").assertIsDisplayed()
        compose.onNodeWithTag("moments_comment_input").assertIsNotFocused()
        compose.onNodeWithText("回复 小白").assertDoesNotExist()
        saveScreenshot("comments-text-menu")
        compose.onNodeWithTag("moments_comment_delete").performClick()
        compose.onNodeWithTag("moments_comment_delete_dialog").assertIsDisplayed()
        assertTrue(deleteRequests.isEmpty())
        saveScreenshot("comments-delete-confirmation")
        compose.onNodeWithTag("moments_comment_delete_cancel").performClick()
        compose.onNodeWithTag("moments_comment_delete_dialog").assertDoesNotExist()
        compose.onNodeWithTag("moment_comment_own-text").assertIsDisplayed()
        assertTrue(deleteRequests.isEmpty())
    }

    @Test
    fun longPressText_confirmDelete_hidesOnlySelectedComment() {
        openTextMenu()
        compose.onNodeWithTag("moments_comment_delete").performClick()
        compose.onNodeWithTag("moments_comment_delete_confirm").performClick()
        compose.onNodeWithTag("moment_comment_own-text").assertDoesNotExist()
        compose.onNodeWithTag("moment_comment_own-image").assertIsDisplayed()
        compose.onNodeWithTag("moment_comment_other-text").assertIsDisplayed()
        assertEquals(listOf("own-text"), deleteRequests)
    }

    @Test
    fun longPressImage_opensDeleteMenu_withoutOpeningPreview() {
        compose.onNodeWithTag("moment_comment_image_own-image").performTouchInput { longClick() }
        compose.onNodeWithTag("moments_comment_actions").assertIsDisplayed()
        compose.onNodeWithTag("moments_comment_copy").assertDoesNotExist()
        compose.onNodeWithTag("moments_comment_delete").assertIsDisplayed()
        compose.onNodeWithTag("moments_photo_preview_dialog").assertDoesNotExist()
        compose.onNodeWithTag("moments_comment_input").assertIsNotFocused()
        saveScreenshot("comments-image-menu")
        pressBack()
        compose.onNodeWithTag("moments_comment_actions").assertDoesNotExist()
        assertTrue(deleteRequests.isEmpty())
    }

    @Test
    fun tapCommentAndImage_preservesReplyAndPreview() {
        compose.onNodeWithTag("moment_comment_own-text").performTouchInput { click() }
        compose.onNodeWithTag("moments_comment_input").assertIsFocused()
        compose.onNodeWithText("回复 小白").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("moment_comment_image_own-image").performTouchInput { click() }
        compose.onNodeWithTag("moments_photo_preview_dialog").assertIsDisplayed()
        compose.onNodeWithTag("moments_comment_actions").assertDoesNotExist()
    }

    @Test
    fun longPressOtherAuthorsComment_showsCopyWithoutDelete() {
        compose.onNodeWithTag("moment_comment_other-text").performTouchInput { longClick() }
        compose.onNodeWithTag("moments_comment_copy").assertIsDisplayed()
        compose.onNodeWithTag("moments_comment_delete").assertDoesNotExist()
        assertTrue(deleteRequests.isEmpty())
    }

    @Test
    fun copyComment_copiesOnlyBodyAndClosesMenu() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val previousClip = clipboard.primaryClip
        try {
            openTextMenu()
            compose.onNodeWithTag("moments_comment_copy").performClick()
            compose.waitUntil(timeoutMillis = 3000) { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == "😀😀" }
            compose.onNodeWithTag("moments_comment_actions").assertDoesNotExist()
            assertTrue(deleteRequests.isEmpty())
        } finally {
            if (previousClip != null) clipboard.setPrimaryClip(previousClip) else clipboard.clearPrimaryClip()
        }
    }

    @Test
    fun tapOutsideMenu_closesMenuWithoutSubmitting() {
        val outside = compose.onNodeWithText("评论交互测试").fetchSemanticsNode().boundsInWindow.center
        openTextMenu()
        // Compose 的触摸派发只进入 Activity；跨 Popup 窗口的外部点击需要系统触摸。
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).click(outside.x.roundToInt(), outside.y.roundToInt())
        compose.onNodeWithTag("moments_comment_actions").assertDoesNotExist()
        assertTrue(deleteRequests.isEmpty())
    }

    @Test
    fun confirmDelete_failure_keepsCommentAndShowsError() {
        failDelete = true
        openTextMenu()
        compose.onNodeWithTag("moments_comment_delete").performClick()
        compose.onNodeWithTag("moments_comment_delete_confirm").performClick()
        compose.onNodeWithTag("moment_comment_own-text").assertIsDisplayed()
        compose.onNodeWithText("操作未完成").assertIsDisplayed()
        compose.onNodeWithTag("moments_comment_delete_dialog").assertDoesNotExist()
        assertEquals(listOf("own-text"), deleteRequests)
    }

    @Test
    fun accountChangesWhileConfirming_closesDialogWithoutSubmitting() {
        openTextMenu()
        compose.onNodeWithTag("moments_comment_delete").performClick()
        compose.runOnIdle { account.value = "xiaohuang" }
        compose.onNodeWithTag("moments_comment_delete_dialog").assertDoesNotExist()
        compose.onNodeWithTag("moment_comment_own-text").performTouchInput { longClick() }
        compose.onNodeWithTag("moments_comment_delete").assertDoesNotExist()
        assertTrue(deleteRequests.isEmpty())
    }

    @Test
    fun commentDeletedWhileConfirming_closesDialogWithoutSubmitting() {
        openTextMenu()
        compose.onNodeWithTag("moments_comment_delete").performClick()
        compose.runOnIdle {
            post.value = post.value.copy(comments = post.value.comments.map { if (it.id == "own-text") it.copy(deleted = true) else it })
        }
        compose.onNodeWithTag("moments_comment_delete_dialog").assertDoesNotExist()
        compose.onNodeWithTag("moment_comment_own-text").assertDoesNotExist()
        assertTrue(deleteRequests.isEmpty())
    }

    private fun openTextMenu() {
        compose.onNodeWithTag("moment_comment_own-text").performTouchInput { longClick() }
        compose.onNodeWithTag("moments_comment_actions").assertIsDisplayed()
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose 空闲不包含原生 Dialog 的进场动画，等窗口稳定后再留验证截图。
        instrumentation.uiAutomation.waitForIdle(500, 3000)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "comment-ui-validation").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
