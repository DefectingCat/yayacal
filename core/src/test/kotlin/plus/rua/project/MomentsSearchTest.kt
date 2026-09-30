package plus.rua.project

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 只验证本机存储文本搜索，不涉及图片识别或远端朋友圈内容。 */
class MomentsSearchTest {
    @Test
    fun searchMoments_emptyQuery_returnsNoResults() {
        val posts = listOf(MomentPost(text = "猫猫"))

        assertEquals(emptyList(), searchMoments(posts, ""))
        assertEquals(emptyList(), searchMoments(posts, " \t\n "))
        assertEquals(emptyList(), searchMoments(emptyList(), "猫猫"))
    }

    @Test
    fun searchMoments_chineseAndLatinQuery_matchesTrimmedLiteralIgnoringCase() {
        val chinese = MomentPost(id = "chinese", text = "今天遇到了猫猫")
        val latin = MomentPost(id = "latin", text = "My CAT is sleeping")
        val posts = listOf(chinese, latin)

        assertEquals(listOf(chinese), searchMoments(posts, " 猫猫 \n").map { it.post })
        assertEquals(listOf(latin), searchMoments(posts, "cat").map { it.post })
        assertEquals("My CAT is sleeping", searchMoments(posts, "cat").single().previewText)
    }

    @Test
    fun searchMoments_locationAddressMatch_usesAddressAsPreview() {
        val post = MomentPost(location = "咖啡店", locationAddress = "广东省深圳市南山区")

        val result = searchMoments(listOf(post), "深圳").single()

        assertEquals(listOf("位置"), result.matchedFields)
        assertEquals("广东省深圳市南山区", result.previewText)
    }

    @Test
    fun searchMoments_commentMatch_usesFirstMatchingCommentText() {
        val post = MomentPost(
            text = "周末出门",
            comments = listOf(
                MomentComment(authorName = "鸭鸭", text = "天气真好"),
                MomentComment(authorName = "鸭鸭", text = "猫猫好可爱\n下次再来"),
                MomentComment(authorName = "鸭鸭", text = "还想看猫猫"),
            ),
        )

        val result = searchMoments(listOf(post), "猫猫").single()

        assertEquals(listOf("评论"), result.matchedFields)
        assertEquals("猫猫好可爱\n下次再来", result.previewText)
    }

    @Test
    fun searchMoments_multipleFields_deduplicatesLabelsAndPrioritizesBody() {
        val post = MomentPost(
            text = "在公园遇见猫猫",
            location = "猫猫公园",
            locationAddress = "猫猫公园北门",
            comments = listOf(
                MomentComment(authorName = "鸭鸭", text = "猫猫很可爱"),
                MomentComment(authorName = "鸭鸭", text = "想摸猫猫"),
            ),
        )

        val results = searchMoments(listOf(post), "猫猫")

        assertEquals(1, results.size)
        assertEquals(listOf("正文", "位置", "评论"), results.single().matchedFields)
        assertEquals("在公园遇见猫猫", results.single().previewText)
    }

    @Test
    fun searchMoments_locationAndCommentMatch_prioritizesLocationName() {
        val post = MomentPost(
            location = "猫猫公园",
            locationAddress = "猫猫公园北门",
            comments = listOf(MomentComment(authorName = "鸭鸭", text = "猫猫很可爱")),
        )

        val result = searchMoments(listOf(post), "猫猫").single()

        assertEquals(listOf("位置", "评论"), result.matchedFields)
        assertEquals("猫猫公园", result.previewText)
    }

    @Test
    fun searchMoments_privatePosts_includedInNewestFirstOrder() {
        val older = MomentPost(id = "older", text = "猫猫", timestamp = 100L)
        val privatePost = MomentPost(id = "private", text = "猫猫", timestamp = 300L, visibility = "私密")
        val middle = MomentPost(id = "middle", text = "猫猫", timestamp = 200L)

        assertEquals(listOf(privatePost, middle, older), searchMoments(listOf(middle, older, privatePost), "猫猫").map { it.post })
    }

    @Test
    fun searchMoments_metadataOnlyMatch_returnsNoResults() {
        val post = MomentPost(
            id = "猫猫",
            photoPaths = listOf("/private/猫猫.jpg"),
            visibility = "猫猫",
            comments = listOf(
                MomentComment(
                    id = "猫猫",
                    authorName = "猫猫",
                    replyToName = "猫猫",
                    text = "很可爱",
                    photoPath = "/private/猫猫.jpg",
                ),
            ),
        )

        assertEquals(emptyList(), searchMoments(listOf(post), "猫猫"))
    }

    @Test
    fun searchMoments_longText_keepsFirstHitWithContextAndEllipses() {
        val post = MomentPost(text = "前".repeat(100) + "猫猫" + "后".repeat(100) + "猫猫")

        val result = searchMoments(listOf(post), "猫猫").single()

        assertEquals("…" + "前".repeat(20) + "猫猫" + "后".repeat(60) + "…", result.previewText)
    }

    @Test
    fun searchMoments_previewBoundary_keepsEmojiSurrogatePairs() {
        val post = MomentPost(text = "前".repeat(30) + "🐱" + "前".repeat(19) + "猫猫" + "后".repeat(59) + "🐶" + "后".repeat(30))

        val result = searchMoments(listOf(post), "猫猫").single()

        assertEquals("…🐱" + "前".repeat(19) + "猫猫" + "后".repeat(59) + "🐶…", result.previewText)
    }

    @Test
    fun searchMoments_punctuationQuery_isLiteralAndPreservesWhitespace() {
        val literal = MomentPost(id = "literal", text = "猫猫 [a.*] +\n 今天")
        val other = MomentPost(id = "other", text = "猫猫 abc 今天")

        val results = searchMoments(listOf(literal, other), "[a.*]")

        assertEquals(listOf(literal), results.map { it.post })
        assertEquals(literal.text, results.single().previewText)
        assertTrue(searchMoments(listOf(other), ".*").isEmpty())
    }
}
