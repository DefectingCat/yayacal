package plus.rua.project.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import plus.rua.project.MomentPost
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class MomentsAlbumLogicTest {

    private val tz = TimeZone.of("Asia/Shanghai")

    @Test
    fun groupPostsForAlbum_emptyList_returnsEmpty() {
        val result = groupPostsForAlbum(emptyList(), tz)
        assertTrue(result.isEmpty())
    }

    @Test
    fun groupPostsForAlbum_singlePost_correctlyGrouped() {
        // 2025-10-16 10:00:00 UTC+8 -> 1760580000000L
        val dt = kotlinx.datetime.LocalDateTime(2025, 10, 16, 10, 0, 0)
        val instant = kotlinx.datetime.LocalDateTime(2025, 10, 16, 10, 0, 0)
        val ts = 1760580000000L
        val post = MomentPost(id = "cat_1", photoPaths = listOf("cat.jpg"), timestamp = ts)

        val result = groupPostsForAlbum(listOf(post), tz)
        assertEquals(1, result.size)
        assertEquals(2025, result[0].year)
        assertEquals(1, result[0].months.size)
        assertEquals(10, result[0].months[0].month)
        assertEquals(1, result[0].months[0].posts.size)
        assertEquals("cat_1", result[0].months[0].posts[0].id)
    }

    @Test
    fun groupPostsForAlbum_multipleYearsAndMonths_sortedDescending() {
        val post2024May = MomentPost(id = "p1", timestamp = 1715000000000L) // 2024 May
        val post2025Mar = MomentPost(id = "p2", timestamp = 1742000000000L) // 2025 Mar
        val post2025OctEarly = MomentPost(id = "p3", timestamp = 1760500000000L) // 2025 Oct early
        val post2025OctLate = MomentPost(id = "p4", timestamp = 1760600000000L) // 2025 Oct late
        val post2026Jan = MomentPost(id = "p5", timestamp = 1768000000000L) // 2026 Jan

        val posts = listOf(post2024May, post2025Mar, post2025OctEarly, post2025OctLate, post2026Jan)
        val result = groupPostsForAlbum(posts, tz)

        assertEquals(listOf(2026, 2025, 2024), result.map { it.year })

        val g2025 = result.first { it.year == 2025 }
        assertEquals(listOf(10, 3), g2025.months.map { it.month })

        val octPosts = g2025.months.first { it.month == 10 }.posts
        assertEquals(listOf("p4", "p3"), octPosts.map { it.id })
    }

    @Test
    fun groupPostsForAlbum_preservesVisibilityAndText() {
        val privatePost = MomentPost(
            id = "priv_1",
            text = "私密日记",
            visibility = "私密",
            timestamp = 1760580000000L,
        )
        val result = groupPostsForAlbum(listOf(privatePost), tz)
        assertEquals("私密", result[0].months[0].posts[0].visibility)
        assertEquals("私密日记", result[0].months[0].posts[0].text)
        assertTrue(result[0].months[0].posts[0].photoPaths.isEmpty())
    }
}
