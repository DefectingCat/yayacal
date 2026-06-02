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
 * 测试覆盖全部用户交互路径，实现全量 AOT：
 * 1.  冷启动 → 首帧渲染
 * 2.  显示调休切换 ON/OFF（DayCell 大规模重组 + staggered 动画）
 * 3.  CalendarPager 翻页 → "今天"按钮跳回
 * 4.  跨月日期点击 → 自动跳转
 * 5.  月视图 → 年视图切换
 * 6.  年视图翻年 → "今年"按钮跳回
 * 7.  年视图点击 MiniMonth 返回月视图
 * 8.  DayCell 点击
 * 9.  BottomCard 拖拽折叠到周视图
 * 10. 周视图左右翻页
 * 11. BottomCard 拖拽展开回月视图
 * 12. 工具页面 → 日期检查器
 * 13. DatePickerDialog 打开/确认
 * 14. 日期检查器添加行 + 输入天数
 * 15. 日期检查器滑动删除行
 * 16. 关于页面 → 开源许可页面
 * 17. 返回主界面 → CalendarPager 翻页
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private fun MacrobenchmarkScope.safeFindFab(): UiObject2? =
        device.wait(Until.findObject(By.res("fab_menu")), 3000)

    private fun MacrobenchmarkScope.safeWaitCalendarPager(timeout: Long = 5000): UiObject2? =
        device.wait(Until.findObject(By.res("calendar_pager")), timeout)

    private fun MacrobenchmarkScope.waitForMainActivity() {
        for (i in 1..5) {
            val pager = device.wait(Until.findObject(By.res("calendar_pager")), 2000)
            if (pager != null) return
            val fab = device.wait(Until.findObject(By.res("fab_menu")), 1000)
            if (fab != null) return
            Thread.sleep(500)
        }
    }

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
                val todayBtn = device.wait(Until.findObject(By.text("今天")), 3000)
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

                // ── 7. 切换到年视图 ──────────────────────────────────────
                val fab2 = safeFindFab()
                assertNotNull("FAB 必须存在（年视图）", fab2)
                fab2!!.click()
                device.waitForIdle()
                val yearViewItem = device.wait(
                    Until.findObject(By.text("年视图")), 3000
                )
                assertNotNull("年视图必须出现", yearViewItem)
                yearViewItem!!.click()
                val yearGrid = device.wait(
                    Until.findObject(By.res("year_grid")), 3000
                )
                assertNotNull("YearGridView 必须加载", yearGrid)
                device.waitForIdle()

                // ── 8. 左滑年视图 → 下一年（HorizontalPager） ─────────────
                yearGrid!!.swipe(Direction.LEFT, 0.8f)
                device.waitForIdle()
                Thread.sleep(500)

                // ── 9. 尝试点击"今年"按钮跳回当前年 ──────────────────
                val thisYearBtn = device.wait(
                    Until.findObject(By.text("今年")), 2000
                )
                if (thisYearBtn != null && thisYearBtn.visibleBounds.height() > 0) {
                    thisYearBtn.click()
                    device.waitForIdle()
                    Thread.sleep(500)
                }

                // ── 10. 点击当前月份 MiniMonth 返回月视图 ───────────────
                val now = java.time.LocalDate.now()
                val currentMonthDesc = "${now.year} 年 ${now.monthValue} 月"
                val miniMonth = device.wait(
                    Until.findObject(By.desc(currentMonthDesc)), 3000
                )
                if (miniMonth != null) {
                    miniMonth.click()
                } else {
                    val yearGridRef = device.wait(Until.findObject(By.res("year_grid")), 3000)
                    if (yearGridRef != null) {
                        val ygBounds = yearGridRef.visibleBounds
                        val monthW = ygBounds.width() / 3
                        val monthH = ygBounds.height() / 4
                        val mIdx = now.monthValue - 1
                        device.click(
                            ygBounds.left + (mIdx % 3) * monthW + monthW / 2,
                            ygBounds.top + (mIdx / 3) * monthH + monthH / 2
                        )
                    }
                }
                Thread.sleep(1500)
                device.waitForIdle()
                safeWaitCalendarPager(5000)
                device.waitForIdle()

                // ── 11. 点击 DayCell ────────────────────────────────────
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

                // ── 12. 拖拽 BottomCard 折叠到周视图 ─────────────────────
                val bottomCard = device.wait(Until.findObject(By.res("bottom_card")), 5000)
                assertNotNull("BottomCard 必须存在", bottomCard)
                val bcBounds = bottomCard!!.visibleBounds
                val cx = bcBounds.centerX()
                val cy = bcBounds.centerY()
                val dragDist = (bcBounds.height() * 0.4).toInt()
                device.drag(cx, cy, cx, cy - dragDist, 20)
                device.waitForIdle()

                // ── 13. 周视图左右翻页 ──────────────────────────────────
                val weekPager = safeWaitCalendarPager(3000)
                assertNotNull("周视图 CalendarPager 必须存在", weekPager)
                weekPager!!.swipe(Direction.LEFT, 0.5f)
                device.waitForIdle()
                weekPager.swipe(Direction.RIGHT, 0.5f)
                device.waitForIdle()

                // ── 14. 拖拽 BottomCard 展开回月视图 ─────────────────────
                device.drag(cx, cy - dragDist, cx, cy, 20)
                device.waitForIdle()

                // ── 15. 切换"显示调休"OFF ────────────────────────────────
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

                // ── 16. CalendarPager 左右翻页 ──────────────────────────
                val mainPager = safeWaitCalendarPager(5000)
                if (mainPager != null) {
                    mainPager.swipe(Direction.LEFT, 0.5f)
                    device.waitForIdle()
                    safeWaitCalendarPager(3000)?.swipe(Direction.RIGHT, 0.5f)
                    device.waitForIdle()
                }

                // ── 17. 进入关于页面 ────────────────────────────────────
                val fab5 = safeFindFab()
                assertNotNull("FAB 必须存在（关于）", fab5)
                fab5!!.click()
                device.waitForIdle()
                val aboutButton = device.wait(
                    Until.findObject(By.text("关于")), 3000
                )
                assertNotNull("关于必须出现", aboutButton)
                aboutButton!!.click()
                device.waitForIdle()

                // ── 18. 进入开源许可页面 ────────────────────────────────
                val licensesButton = device.wait(
                    Until.findObject(By.text("开放源代码许可")), 3000
                )
                assertNotNull("开放源代码许可按钮必须存在", licensesButton)
                licensesButton!!.click()
                device.waitForIdle()

                // ── 19. 等待许可列表加载 ────────────────────────────────
                device.wait(Until.findObject(By.textContains("Apache")), 2000)

                // ── 20. 返回关于页 ──────────────────────────────────────
                device.pressBack()
                device.waitForIdle()

                // ── 21. 返回主界面 ──────────────────────────────────────
                device.pressBack()
                device.waitForIdle()

                // ── 22. 进入工具页面 ────────────────────────────────────
                val fab4 = safeFindFab()
                assertNotNull("FAB 必须存在（工具）", fab4)
                fab4!!.click()
                device.waitForIdle()
                val toolsButton = device.wait(
                    Until.findObject(By.text("工具")), 3000
                )
                assertNotNull("工具必须出现", toolsButton)
                toolsButton!!.click()
                device.waitForIdle()

                // ── 23. 进入日期检查器 ──────────────────────────────────
                val dateCheckerEntry = device.wait(
                    Until.findObject(By.res("tool_date_checker")), 3000
                )
                assertNotNull("日期检查器入口必须存在", dateCheckerEntry)
                dateCheckerEntry!!.click()
                device.waitForIdle()

                // ── 24. 打开生产日期 DatePicker → 确定 ───────────────────
                val datePickerBtn = device.wait(
                    Until.findObject(By.res("date_picker_button")), 3000
                )
                if (datePickerBtn != null) {
                    datePickerBtn.click()
                    device.waitForIdle()
                    val confirmBtn = device.wait(
                        Until.findObject(By.text("确定")), 2000
                    )
                    if (confirmBtn != null) {
                        confirmBtn.click()
                        device.waitForIdle()
                    }
                }

                // ── 25. FAB 添加新行 ────────────────────────────────────
                val dateCheckerFab = device.wait(
                    Until.findObject(By.res("date_checker_fab")), 3000
                )
                assertNotNull("DateChecker FAB 必须存在", dateCheckerFab)
                dateCheckerFab!!.click()
                device.waitForIdle()

                // ── 26. 在新行输入天数 ──────────────────────────────────
                val screenW = device.displayWidth
                val screenH = device.displayHeight
                device.click((screenW * 0.35f).toInt(), (screenH * 0.80f).toInt())
                Thread.sleep(500)
                device.executeShellCommand("input text '90'")
                device.waitForIdle()
                device.click(screenW / 2, (screenH * 0.15f).toInt())
                device.waitForIdle()

                // ── 27. 滑动删除新行（SwipeToDismiss） ───────────────────
                device.swipe(
                    (screenW * 0.85f).toInt(), (screenH * 0.75f).toInt(),
                    (screenW * 0.15f).toInt(), (screenH * 0.75f).toInt(),
                    30
                )
                Thread.sleep(800)
                device.waitForIdle()

                Log.d(TAG, "Baseline profile 生成完成，所有路径已覆盖")
            }
        )
    }
}
