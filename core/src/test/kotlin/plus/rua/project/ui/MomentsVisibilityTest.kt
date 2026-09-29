package plus.rua.project.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 微信朋友圈“谁可以看”可见性常量与交互逻辑单元测试。
 */
class MomentsVisibilityTest {

    @Test
    fun constants_defineCorrectChineseLabels() {
        assertEquals("公开", MomentsVisibilityConstants.PUBLIC)
        assertEquals("私密", MomentsVisibilityConstants.PRIVATE)
        assertEquals("部分可见", MomentsVisibilityConstants.PARTIAL)
        assertEquals("不给谁看", MomentsVisibilityConstants.EXCLUDE)
    }

    @Test
    fun defaultTags_containExpectedCommonCategories() {
        assertTrue(MomentsVisibilityConstants.DEFAULT_TAGS_PARTIAL.contains("家人"))
        assertTrue(MomentsVisibilityConstants.DEFAULT_TAGS_PARTIAL.contains("朋友"))
        assertTrue(MomentsVisibilityConstants.DEFAULT_TAGS_EXCLUDE.contains("同事"))
        assertTrue(MomentsVisibilityConstants.DEFAULT_TAGS_EXCLUDE.contains("工作"))
    }

    @Test
    fun tagSelectionLogic_togglesCorrectly() {
        val selected = mutableListOf<String>()

        // 添加标签
        fun toggleTag(tag: String) {
            if (selected.contains(tag)) {
                selected.remove(tag)
            } else {
                selected.add(tag)
            }
        }

        toggleTag("家人")
        assertTrue(selected.contains("家人"))
        assertEquals(listOf("家人"), selected)

        toggleTag("同事")
        assertEquals(listOf("家人", "同事"), selected)

        toggleTag("家人")
        assertFalse(selected.contains("家人"))
        assertEquals(listOf("同事"), selected)
    }
}
