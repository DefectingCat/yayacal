package plus.rua.project.baseline

import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 自动生成器。
 *
 * 运行方式（一键生成 + 自动复制到 :core）：
 * ```
 * ./gradlew :macrobenchmark:updateBaselineProfile
 * ```
 *
 * 仅运行基准测试（不自动复制）：
 * ```
 * ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
 * ```
 *
 * 手动复制路径：
 * `macrobenchmark/build/outputs/connected_android_test_additional_output/`
 *
 * 测试覆盖启动与核心用户交互路径，实现关键路径 AOT：
 * 1.  冷启动 → 首帧渲染
 * 2.  显示调休切换 ON/OFF（DayCell 大规模重组 + staggered 动画）
 * 3.  CalendarPager 翻页 → "今天"按钮跳回
 * 4.  跨月日期点击 → 自动跳转
 * 5.  DayCell 点击
 * 6.  BottomCard 拖拽折叠到周视图
 * 7.  周视图左右翻页
 * 8.  BottomCard 拖拽展开回月视图
 * 9.  CalendarPager 左右翻页
 *
 * 注：年视图、关于/开源许可、工具/日期检查器等路径在部分模拟器上不稳定，
 * 暂时从生成流程中移除以保证 Baseline Profile 可稳定生成。后续可在真机上扩展覆盖。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private fun MacrobenchmarkScope.safeFindFab(): UiObject2? =
        device.wait(Until.findObject(By.res("fab_menu")), 5000)

    private fun MacrobenchmarkScope.safeWaitCalendarPager(timeout: Long = 5000): UiObject2? =
        device.wait(Until.findObject(By.res("calendar_pager")), timeout)

    @Test
    fun generateAppStartupProfile() {
        baselineProfileRule.collect(
            packageName = "plus.rua.project",
            includeInStartupProfile = true,
            profileBlock = {
                val TAG = "BaselineProfile"

                // ── 1. 冷启动 ──────────────────────────────────────────
                pressHome()
                device.executeShellCommand(
                    "am start -W -n plus.rua.project/.MainActivity"
                )
                device.waitForIdle()

                // ── 2. 切换"显示调休"ON（覆盖 DayCell 大规模重组 + staggered 动画） ──
                val fab1 = safeFindFab()
                assertNotNull("FAB 必须存在", fab1)
                fab1!!.click()
                device.waitForIdle()
                val legalHolidayOn = device.wait(
                    Until.findObject(By.text("显示调休")), 3000
                )
                assertNotNull("显示调休必须出现", legalHolidayOn)
                legalHolidayOn!!.click()
                device.waitForIdle()

                // ── 3. 右滑 CalendarPager → 上一个月 ──────────────────────
                val calendarPager = safeWaitCalendarPager(3000)
                assertNotNull("CalendarPager 必须存在", calendarPager)
                calendarPager!!.swipe(Direction.RIGHT, 0.8f)
                device.waitForIdle()

                // ── 4. 点击"今天"按钮跳回当月（覆盖 MonthHeader 今天按钮） ──
                // 模拟器上 UI 渲染/动画较慢，使用较长超时等待按钮渲染
                val todayBtn = device.wait(Until.findObject(By.text("今天")), 10000)
                assertNotNull("今天按钮必须出现", todayBtn)
                todayBtn!!.click()
                device.waitForIdle()

                // ── 5. 点击跨月日期（网格左上角 = 上月灰色日期） ──────────
                val pagerRef = safeWaitCalendarPager(3000)
                assertNotNull("CalendarPager 必须存在（跨月点击）", pagerRef)
                val pagerBounds = pagerRef!!.visibleBounds
                val colW = pagerBounds.width() / 7
                val rowH = pagerBounds.height() / 6
                device.click(pagerBounds.left + colW / 2, pagerBounds.top + rowH / 2)
                device.waitForIdle()

                // ── 6. 再次点击"今天"跳回当月 ────────────────────────────
                val todayBtn2 = device.wait(Until.findObject(By.text("今天")), 3000)
                if (todayBtn2 != null) {
                    todayBtn2.click()
                    device.waitForIdle()
                }

                // ── 7. 点击 DayCell ────────────────────────────────────
                val todayCell = device.wait(
                    Until.findObject(By.descContains("今天")), 3000
                )
                if (todayCell != null) {
                    todayCell.click()
                } else {
                    val calRef = safeWaitCalendarPager(3000)
                    if (calRef != null) {
                        val cb = calRef.visibleBounds
                        device.click(cb.centerX(), cb.centerY())
                    }
                }
                device.waitForIdle()

                // ── 8. 拖拽 BottomCard 折叠到周视图 ─────────────────────
                val bottomCard = device.wait(Until.findObject(By.res("bottom_card")), 5000)
                assertNotNull("BottomCard 必须存在", bottomCard)
                val bcBounds = bottomCard!!.visibleBounds
                val cx = bcBounds.centerX()
                val cy = bcBounds.centerY()
                val dragDist = (bcBounds.height() * 0.4).toInt()
                device.drag(cx, cy, cx, cy - dragDist, 20)
                device.waitForIdle()

                // ── 9. 周视图左右翻页 ──────────────────────────────────
                val weekPager = safeWaitCalendarPager(3000)
                assertNotNull("周视图 CalendarPager 必须存在", weekPager)
                weekPager!!.swipe(Direction.LEFT, 0.5f)
                device.waitForIdle()
                weekPager.swipe(Direction.RIGHT, 0.5f)
                device.waitForIdle()

                // ── 10. 拖拽 BottomCard 展开回月视图 ─────────────────────
                device.drag(cx, cy - dragDist, cx, cy, 20)
                device.waitForIdle()

                // ── 11. 切换"显示调休"OFF ────────────────────────────────
                val fab3 = safeFindFab()
                assertNotNull("FAB 必须存在（关闭调休）", fab3)
                fab3!!.click()
                device.waitForIdle()
                val legalHolidayOff = device.wait(
                    Until.findObject(By.text("显示调休")), 3000
                )
                assertNotNull("显示调休必须出现", legalHolidayOff)
                legalHolidayOff!!.click()
                device.waitForIdle()

                // ── 12. CalendarPager 左右翻页 ──────────────────────────
                val mainPager = safeWaitCalendarPager(5000)
                if (mainPager != null) {
                    mainPager.swipe(Direction.LEFT, 0.5f)
                    device.waitForIdle()
                    safeWaitCalendarPager(3000)?.swipe(Direction.RIGHT, 0.5f)
                    device.waitForIdle()
                }

                Log.d(TAG, "Baseline profile 生成完成，核心路径已覆盖")
            }
        )
    }
}
