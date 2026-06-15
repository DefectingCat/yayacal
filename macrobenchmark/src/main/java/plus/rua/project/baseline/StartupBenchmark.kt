package plus.rua.project.baseline

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 冷启动性能基准测试。
 *
 * 测量指标：
 * - timeToInitialDisplay：从 intent 到首帧显示
 * - timeToFullDisplay：从 intent 到 reportFullyDrawn()/ReportDrawn() 被触发
 *
 * 运行方式：
 * ```
 * ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest --tests "plus.rua.project.baseline.StartupBenchmark"
 * ```
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupColdFull() = startup(CompilationMode.Full())

    @Test
    fun startupColdPartial() = startup(CompilationMode.Partial())

    @Test
    fun startupColdNone() = startup(CompilationMode.None())

    private fun startup(compilationMode: CompilationMode) {
        benchmarkRule.measureRepeated(
            packageName = "plus.rua.project",
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
            }
        )
    }
}
