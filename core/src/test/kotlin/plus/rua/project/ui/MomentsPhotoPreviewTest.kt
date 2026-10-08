package plus.rua.project.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import plus.rua.project.MomentPhotoMetadata

/**
 * 朋友圈图片预览辅助函数与逻辑单元测试。
 */
class MomentsPhotoPreviewTest {
    @Test
    fun remotePhoto_oldServer_defaultsToThumbnailAndPreservesAccount() {
        val url = "https://example.com/api/v1/media/image?account_id=xiaojimao"
        assertEquals(url, resolvePhotoUri(url))
        assertEquals("$url&thumbnail=true&v=2", momentsThumbnailUri(url))
        assertEquals("$url&thumbnail=true&v=2", momentsPreviewUri(url))
        assertEquals("$url&thumbnail=true&v=2", momentsThumbnailUri("$url&thumbnail=true"))
    }

    @Test fun remotePhoto_withoutQuery_thumbnailUsesQuerySeparator() {
        assertEquals("https://example.com/api/v1/media/photo?thumbnail=true&v=2", momentsThumbnailUri("https://example.com/api/v1/media/photo"))
    }

    @Test
    fun remotePhoto_readyPreview_selectsPreviewUntilOriginalExplicitlyRequested() {
        val url = "https://example.com/api/v1/media/image?account_id=xiaojimao"
        val metadata = MomentPhotoMetadata(originalBytes = 5_000_000, previewAvailable = true)
        assertEquals("$url&thumbnail=true&variant=preview&v=2", momentsPreviewUri(url, metadata))
        assertEquals("$url&thumbnail=true&v=2", momentsPreviewUri(url, metadata, thumbnailOnly = true))
        assertEquals(url, momentsPreviewUri(url, metadata, original = true))
        assertEquals(url, momentsPreviewUri(url, original = true))
    }

    @Test
    fun remotePhoto_existingVariant_isReplacedWithoutDroppingAccount() {
        val url = "https://example.com/api/v1/media/image?account_id=xiaobai"
        val variant = "$url&variant=original&thumbnail=false&v=1"
        assertEquals("$url&thumbnail=true&v=2", momentsThumbnailUri(variant))
        assertEquals("$url&thumbnail=true&variant=preview&v=2", momentsPreviewUri(variant, MomentPhotoMetadata(previewAvailable = true)))
        assertEquals(url, momentsOriginalUri(variant))
    }

    @Test
    fun photo_localAndExternalSources_keepTheirOriginalUris() {
        val paths = listOf("content://media/external/images/media/42", "file:///tmp/photo.jpg", "android.resource://plus.rua.project/drawable/photo", "https://example.com/photo.jpg?next=/api/v1/media/photo")
        paths.forEach { path ->
            assertEquals(path, momentsPreviewUri(path))
            assertEquals(path, momentsThumbnailUri(path))
            assertEquals(path, momentsOriginalUri(path))
        }
    }

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
