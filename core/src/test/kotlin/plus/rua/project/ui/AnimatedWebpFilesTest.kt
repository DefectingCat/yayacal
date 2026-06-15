package plus.rua.project.ui

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/**
 * 守卫：运行期使用的 WebP 文件列表必须与 assets/animations/ 目录实际内容一致。
 *
 * 防止两类回归：
 *  1. 有人把 BuildConfig 方案回退成硬编码 (1..152)，与目录脱钩后增删文件即隐性 bug
 *  2. assets/animations/ 在 CI / 打包流程中意外丢失或被 Git LFS 过滤掉
 *
 * 注：当前实现里 WEBP_FILES 与目录都来自同一次构建期扫描，二者天然一致；
 * 本测试的核心价值是锁定「两者一致」这个不变量，使上述回归一旦发生即立即失败。
 */
class AnimatedWebpFilesTest {

    private val animationsDir = File("src/main/assets/animations")

    @Test
    fun webpFilesMatchDirectoryContents() {
        assertTrue(animationsDir.exists(), "assets/animations 目录应存在: ${animationsDir.absolutePath}")

        val onDisk = animationsDir.listFiles { f -> f.extension.equals("webp", ignoreCase = true) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
        assertTrue(onDisk.isNotEmpty(), "assets/animations 不应为空（若失败请检查 Git LFS / checkout 完整性）")

        // WEBP_FILES 由构建期 BuildConfig 注入（见 core/build.gradle.kts 的 buildConfigField）
        val inCode = WEBP_FILES.sorted()

        assertEquals(onDisk, inCode, "WEBP_FILES 必须与 assets/animations/ 实际 webp 文件一一对应")
    }

    @Test
    fun webpFilesUseZeroPaddedThreeDigitNames() {
        // 锁定命名约定：NNN.webp（三位零填充），防止有人误改成 1.webp / 01.webp 等
        val expected = Regex("""^\d{3}\.webp$""")
        WEBP_FILES.forEach { name ->
            assertTrue(
                expected.matches(name),
                "文件名 $name 不符合 NNN.webp 约定，getWebpUri 与 AnimatedWebp 依赖此格式",
            )
        }
    }
}
