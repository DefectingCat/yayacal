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
 * Baseline Profile / Startup Profile 自动生成器。
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
 * 说明：
 * - 本文件包含两个生成测试：
 *   - `generateBaselineProfile()`：`includeInStartupProfile = false`，生成 `*-baseline-prof.txt`，用于 ART 的 AOT 编译优化
 *   - `generateAppStartupProfile()`：`includeInStartupProfile = true`，生成 `*-startup-prof.txt`，用于 AGP 的 DEX layout 优化
 * - 两个测试执行相同的 `runCoreUserJourney()` 交互路径，因此两份 profile 覆盖范围一致
 * - `updateBaselineProfile` Task 会分别将它们复制到：
 *   - `core/src/main/baseline-prof.txt`
 *   - `core/src/main/baselineProfiles/startup-prof.txt`
 *
 * 测试覆盖启动与核心用户交互路径，用于 AOT 与 DEX layout 优化：
 * 1.  冷启动 → 首帧渲染
 * 2.  切换"显示调休"ON（DayCell 大规模重组 + staggered 动画）
 * 3.  CalendarPager 右滑 → 上一个月
 * 4.  点击"今天"按钮跳回当月
 * 5.  点击跨月日期 → 自动跳转
 * 6.  再次点击"今天"跳回当月
 * 7.  点击 DayCell
 * 8.  拖拽 BottomCard 折叠到周视图
 * 9.  周视图左右翻页
 * 10. 拖拽 BottomCard 展开回月视图
 * 11. 切换"显示调休"OFF
 * 12. CalendarPager 左右翻页
 * 13. 进入年视图（覆盖 SharedTransitionLayout 缩放与年视图网格 Pager）
 * 14. 进入"工具" -> "日期记录器"界面（覆盖新增的工具页与日期记录器功能）
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
    fun generateBaselineProfile() {
        baselineProfileRule.collect(
            packageName = "plus.rua.project",
            includeInStartupProfile = false,
            profileBlock = {
                runCoreUserJourney()
            }
        )
    }

    @Test
    fun generateAppStartupProfile() {
        baselineProfileRule.collect(
            packageName = "plus.rua.project",
            includeInStartupProfile = true,
            profileBlock = {
                runCoreUserJourney()
            }
        )
    }

    private fun MacrobenchmarkScope.runCoreUserJourney() {
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

        // ── 13. 进入年视图与 SharedTransition 穿梭 ───────────────
        val fabYear = safeFindFab()
        if (fabYear != null) {
            fabYear.click()
            device.waitForIdle()
            val yearViewMenu = device.wait(Until.findObject(By.text("年视图")), 3000)
            if (yearViewMenu != null) {
                yearViewMenu.click()
                device.waitForIdle()

                val yearGrid = device.wait(Until.findObject(By.res("year_grid")), 5000)
                if (yearGrid != null) {
                    // 年视图左右翻页
                    yearGrid.swipe(Direction.LEFT, 0.5f)
                    device.waitForIdle()
                    yearGrid.swipe(Direction.RIGHT, 0.5f)
                    device.waitForIdle()

                    // 点击月份小网格切回月视图
                    val bounds = yearGrid.visibleBounds
                    device.click(bounds.centerX(), bounds.centerY())
                    device.waitForIdle()
                } else {
                    // 备用：若未找到 year_grid，通过菜单切回月视图
                    fabYear.click()
                    device.waitForIdle()
                    device.wait(Until.findObject(By.text("月视图")), 3000)?.click()
                    device.waitForIdle()
                }
            }
        }

        // ── 14. 进入"工具" -> "日期记录器"界面 ─────────────────────
        val fabTools = safeFindFab()
        if (fabTools != null) {
            fabTools.click()
            device.waitForIdle()
            val toolsMenu = device.wait(Until.findObject(By.text("工具")), 3000)
            if (toolsMenu != null) {
                toolsMenu.click()
                device.waitForIdle()

                val dateRecorderItem = device.wait(
                    Until.findObject(By.res("tool_date_recorder")), 5000
                ) ?: device.wait(Until.findObject(By.text("日期记录器")), 3000)

                if (dateRecorderItem != null) {
                    dateRecorderItem.click()
                    device.waitForIdle()

                    // 等待日期记录器页面加载
                    device.wait(Until.findObject(By.res("date_recorder_fab")), 5000)
                    device.waitForIdle()

                    // 按返回键返回工具页
                    device.pressBack()
                    device.waitForIdle()
                }

                // 按返回键返回主日历
                device.pressBack()
                device.waitForIdle()
            }
        }

        Log.d(TAG, "Baseline Profile / Startup Profile 生成完成，所有路径已覆盖")
    }
}
