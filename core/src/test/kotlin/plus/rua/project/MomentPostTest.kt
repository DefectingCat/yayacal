package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MomentPostTest {

    @Test
    fun encodeAndDecode_preservesAllFields() {
        val post =
            MomentPost(
                id = "post-123",
                text = "今天天气真好！\n还遇到了小狗🐶，吃了火锅~",
                photoPaths = listOf("/data/user/0/moments/photo_1.jpg", "/data/user/0/moments/photo_2.jpg"),
                location = "创维半导体设计大厦",
                locationAddress = "603m | 广东省深圳市南山区高新南四道18号",
                timestamp = 1759132800000L,
                visibility = "公开",
            )

        val encoded = post.encodeToString()
        val decoded = MomentPost.decodeFromString(encoded)

        assertNotNull(decoded)
        assertEquals(post.id, decoded.id)
        assertEquals(post.text, decoded.text)
        assertEquals(post.photoPaths, decoded.photoPaths)
        assertEquals(post.location, decoded.location)
        assertEquals(post.locationAddress, decoded.locationAddress)
        assertEquals(post.timestamp, decoded.timestamp)
        assertEquals(post.visibility, decoded.visibility)
    }

    @Test
    fun encodeAndDecode_handlesNullLocationAndEmptyPhotos() {
        val post =
            MomentPost(
                id = "post-456",
                text = "纯文字朋友圈",
                photoPaths = emptyList(),
                location = null,
                locationAddress = null,
                timestamp = 1759132800000L,
                visibility = "私密",
            )

        val encoded = post.encodeToString()
        val decoded = MomentPost.decodeFromString(encoded)

        assertNotNull(decoded)
        assertEquals(post.id, decoded.id)
        assertEquals(post.text, decoded.text)
        assertEquals(emptyList(), decoded.photoPaths)
        assertNull(decoded.location)
        assertNull(decoded.locationAddress)
        assertEquals(post.visibility, decoded.visibility)
    }

    @Test
    fun decode_invalidString_returnsNull() {
        assertNull(MomentPost.decodeFromString("invalid|format"))
    }

    @Test
    fun encodeAndDecode_likesAndComments_preservesSeparatorsAndReplies() {
        val post = MomentPost(
            id = "interactions",
            text = "正文 | ; +\n🐱",
            isLikedByMe = true,
            comments = listOf(
                MomentComment(
                    id = "comment-1",
                    authorName = "鸭鸭 | +",
                    text = "回复;带换行\n和表情😊",
                    timestamp = 1234L,
                    replyToName = "朋友;|+",
                    photoPath = "/private/moments/图片 +;.jpg",
                ),
                MomentComment(id = "comment-2", authorName = "鸭鸭", text = "第二条", timestamp = 2345L),
            ),
        )

        assertEquals(post, MomentPost.decodeFromString(post.encodeToString()))
    }

    @Test
    fun decode_legacySevenFields_keepsPostWithEmptyInteractions() {
        val post = MomentPost.decodeFromString("legacy|1000|私密||||%E6%97%A7%E5%8A%A8%E6%80%81")

        assertNotNull(post)
        assertEquals("旧动态", post.text)
        assertEquals(false, post.isLikedByMe)
        assertEquals(emptyList(), post.comments)
    }
}
