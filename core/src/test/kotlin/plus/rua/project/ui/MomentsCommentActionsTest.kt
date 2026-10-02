package plus.rua.project.ui

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import plus.rua.project.MomentComment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MomentsCommentActionsTest {
    private val comment = MomentComment(id = "comment", authorId = "xiaobai", authorName = "小白", text = "😀😀")

    @Test
    fun canDeleteMomentComment_currentAuthor_returnsTrue() {
        assertTrue(canDeleteMomentComment(comment, "xiaobai"))
    }

    @Test
    fun canDeleteMomentComment_otherAccount_returnsFalse() {
        assertFalse(canDeleteMomentComment(comment, "xiaohuang"))
    }

    @Test
    fun canDeleteMomentComment_missingIdentity_doesNotGuessAuthor() {
        assertFalse(canDeleteMomentComment(comment, null))
        assertFalse(canDeleteMomentComment(comment.copy(authorId = ""), ""))
        assertFalse(canDeleteMomentComment(comment.copy(authorId = ""), "xiaobai"))
    }

    @Test
    fun canDeleteMomentComment_deletedComment_returnsFalse() {
        assertFalse(canDeleteMomentComment(comment.copy(deleted = true), "xiaobai"))
    }

    @Test
    fun calculatePosition_roomAbove_centersMenuAboveComment() {
        var below = true
        val provider = MomentCommentMenuPositionProvider(8, 4) { below = it }
        val position = provider.calculatePosition(IntRect(16, 240, 384, 300), IntSize(400, 800), LayoutDirection.Ltr, IntSize(144, 51))
        assertEquals(IntOffset(128, 185), position)
        assertFalse(below)
    }

    @Test
    fun calculatePosition_nearStatusBar_placesMenuBelowComment() {
        var below = false
        val provider = MomentCommentMenuPositionProvider(8, 4, topInset = 24, onPositionCalculated = { below = it })
        val position = provider.calculatePosition(IntRect(16, 30, 384, 90), IntSize(400, 800), LayoutDirection.Ltr, IntSize(144, 51))
        assertEquals(IntOffset(128, 94), position)
        assertTrue(below)
    }

    @Test
    fun calculatePosition_commentAtEitherEdge_keepsMenuInsideWindow() {
        val provider = MomentCommentMenuPositionProvider(8, 4)
        for (direction in LayoutDirection.entries) {
            val left = provider.calculatePosition(IntRect(0, 200, 30, 260), IntSize(400, 800), direction, IntSize(144, 51))
            val right = provider.calculatePosition(IntRect(370, 200, 400, 260), IntSize(400, 800), direction, IntSize(144, 51))
            assertEquals(8, left.x)
            assertEquals(248, right.x)
        }
    }

    @Test
    fun calculatePosition_keyboardVisible_staysAboveKeyboard() {
        val provider = MomentCommentMenuPositionProvider(8, 4, topInset = 24, bottomInset = 300)
        val position = provider.calculatePosition(IntRect(16, 650, 384, 710), IntSize(400, 800), LayoutDirection.Ltr, IntSize(144, 51))
        assertTrue(position.y >= 32)
        assertTrue(position.y + 51 <= 492)
    }

    @Test
    fun calculatePosition_smallWindow_keepsMenuWithinSafeArea() {
        val provider = MomentCommentMenuPositionProvider(8, 4, topInset = 24, bottomInset = 16)
        val window = IntSize(200, 180)
        val menu = IntSize(144, 51)
        for (anchor in listOf(IntRect(8, 28, 192, 60), IntRect(8, 110, 192, 154))) {
            val position = provider.calculatePosition(anchor, window, LayoutDirection.Ltr, menu)
            assertTrue(position.x >= 8)
            assertTrue(position.x + menu.width <= window.width - 8)
            assertTrue(position.y >= 32)
            assertTrue(position.y + menu.height <= window.height - 24)
        }
    }
}
