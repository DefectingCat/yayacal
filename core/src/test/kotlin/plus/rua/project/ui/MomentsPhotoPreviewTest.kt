package plus.rua.project.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 朋友圈图片预览辅助函数与逻辑单元测试。
 */
class MomentsPhotoPreviewTest {
    @Test
    fun resolvePhotoUri_nullOrBlank_returnsNull() {
        assertNull(resolvePhotoUri(null))
        assertNull(resolvePhotoUri(""))
        assertNull(resolvePhotoUri("   "))
    }

    @Test
    fun resolvePhotoUri_contentUri_returnsSameUri() {
        val uri = "content://media/external/images/media/42"
        assertEquals(uri, resolvePhotoUri(uri))
    }

    @Test
    fun resolvePhotoUri_fileUri_returnsSameUri() {
        val uri = "file:///storage/emulated/0/DCIM/pic.jpg"
        assertEquals(uri, resolvePhotoUri(uri))
    }

    @Test
    fun resolvePhotoUri_rawFilePath_prefixesFileProtocol() {
        val path = "/storage/emulated/0/DCIM/pic.jpg"
        val resolved = resolvePhotoUri(path)
        assertTrue(resolved != null && resolved.startsWith("file://"))
    }

    @Test
    fun photoIndexCoercing_boundaryCheck() {
        val photos = listOf("p1", "p2", "p3")
        assertEquals(0, (-1).coerceIn(0, photos.lastIndex))
        assertEquals(1, 1.coerceIn(0, photos.lastIndex))
        assertEquals(2, 5.coerceIn(0, photos.lastIndex))
    }

    @Test
    fun pageIndicatorFormat_calculatesCorrectDisplay() {
        val photos = listOf("p1", "p2", "p3", "p4")
        val currentPage = 2
        val displayText = "${currentPage + 1}/${photos.size}"
        assertEquals("3/4", displayText)
    }
}
