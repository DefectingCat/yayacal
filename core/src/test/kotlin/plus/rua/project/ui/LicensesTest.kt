package plus.rua.project.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 开放源代码许可列表测试。
 */
class LicensesTest {

    @Test
    fun `licenses list is non empty and contains valid fields`() {
        assertTrue(licenses.isNotEmpty(), "许可列表不应为空")
        licenses.forEach { item ->
            assertTrue(item.library.isNotBlank(), "库名称不应为空")
            assertTrue(item.license.isNotBlank(), "许可证名称不应为空")
            assertTrue(item.description.isNotBlank(), "库描述不应为空")
            assertTrue(item.category.isNotBlank(), "库分类不应为空")
            if (item.url != null) {
                assertTrue(item.url.startsWith("http://") || item.url.startsWith("https://"), "URL 应为有效 HTTP(S) 地址")
            }
        }
    }

    @Test
    fun `licenses list contains core dependencies`() {
        val libraries = licenses.map { it.library }
        assertTrue(libraries.contains("AndroidX Activity Compose"))
        assertTrue(libraries.contains("Kotlin"))
        assertTrue(libraries.contains("kotlinx-datetime"))
        assertTrue(libraries.contains("tyme4kt"))
        assertTrue(libraries.contains("Sketch"))
        assertTrue(libraries.contains("ZoomImage"))
        assertTrue(libraries.contains("AndroidX Room"))
    }
}
