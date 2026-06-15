#!/bin/bash
#
# YaYa 性能追踪脚本（场景化版）
# 支持按交互场景录制 Perfetto trace，精准定位各场景性能
#
# 用法:
#   ./scripts/profile.sh                    # 默认抓取 8 秒（向后兼容）
#   ./scripts/profile.sh --scenario month_browse --trace 15
#   ./scripts/profile.sh --list-scenarios   # 列出所有场景
#   ./scripts/profile.sh --help             # 帮助
#

set -euo pipefail

PACKAGE="plus.rua.project"
MAIN_ACTIVITY="plus.rua.project.MainActivity"
TOOLS_ACTIVITY="plus.rua.project.ToolsActivity"
DATECHECKER_ACTIVITY="plus.rua.project.DateCheckerActivity"
ABOUT_ACTIVITY="plus.rua.project.AboutActivity"
LICENSES_ACTIVITY="plus.rua.project.LicensesActivity"

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS_DIR="${PROJECT_ROOT}/logs"

# 默认参数
DURATION_SEC=8
NO_LAUNCH=false
USE_TRACE_BUILD=false
SCENARIO=""
SCENARIO_NAME=""
SCENARIO_DESC=""
BG_PID=""
RUN_ALL=false

# ──────────────────────────────────────────
# 场景列表
# ──────────────────────────────────────────
list_scenarios() {
    cat <<'EOF'
可用场景:

主日历场景:
  month_browse    - 月视图左右滑动翻页（自动滑动）
  date_select     - 点击不同日期（自动：依次点击多个日期）
  collapse_expand - 折叠/展开 月视图↔周视图（自动拖拽）
  year_view       - 年视图切换+年份滑动（自动）
  year_select     - 年视图中选择月份返回（自动）
  today_jump      - 跳转到今天（自动）
  menu_toggle     - FAB 菜单打开关闭（自动）
  legal_holiday   - 显示调休切换（自动）
  cross_month     - 跨月日期选择（自动：点击灰色日期）

其他页面:
  tools           - 工具页面（自动打开）
  date_checker    - 日期检查器（自动：添加行、输入天数、滑动删除）
  about           - 关于页面（自动打开）
  licenses        - 开源许可列表（自动打开+滑动）

综合场景:
  full_flow       - 完整用户流程（自动：串联所有主要交互）
  all_activities  - 遍历所有 Activity（自动）

批量录制:
  --all           - 自动依次录制所有自动场景，最后生成汇总报告

默认场景（向后兼容）:
  （不指定 --scenario）启动应用，抓取指定时长
EOF
}

# ──────────────────────────────────────────
# 参数解析
# ──────────────────────────────────────────
while [ $# -gt 0 ]; do
    case "$1" in
        --no-launch)
            NO_LAUNCH=true
            shift
            ;;
        --trace)
            USE_TRACE_BUILD=true
            shift
            ;;
        --scenario)
            if [ $# -lt 2 ]; then
                echo "错误: --scenario 需要指定场景名"
                exit 1
            fi
            SCENARIO="$2"
            shift 2
            ;;
        --all)
            RUN_ALL=true
            shift
            ;;
        --list-scenarios)
            list_scenarios
            exit 0
            ;;
        --help|-h)
            cat <<EOF
用法: $0 [秒数] [--no-launch] [--trace] [--scenario <场景>] [--all]

选项:
  秒数                抓取时长（默认 8 秒，--all 时每个场景默认 10 秒）
  --no-launch         不自动启动应用
  --trace             使用 trace 构建（release + trace 标记保留）
  --scenario <场景>   指定交互场景，脚本会引导或自动执行对应操作
  --all               批量录制所有自动场景，生成汇总报告
  --list-scenarios    列出所有可用场景
  --help              显示此帮助

示例:
  # 录制月视图滑动翻页 15 秒
  $0 --scenario month_browse --trace 15

  # 录制完整用户流程 30 秒（需手动按提示操作）
  $0 --scenario full_flow --trace 30

  # 录制日期检查器操作（需手动）
  $0 --scenario date_checker --trace 20

  # 批量录制所有自动场景（每个 10 秒）
  $0 --all --trace 10

  # 默认录制（向后兼容）
  $0 --trace 15
EOF
            exit 0
            ;;
        [0-9]*)
            DURATION_SEC="$1"
            shift
            ;;
        *)
            echo "未知参数: $1"
            echo "使用 --help 查看帮助"
            exit 1
            ;;
    esac
done

# ──────────────────────────────────────────
# 工具函数
# ──────────────────────────────────────────

# 获取屏幕尺寸
get_screen_size() {
    adb shell wm size 2>/dev/null | awk '{print $3}'
}

# 获取屏幕中心坐标
get_screen_center() {
    local size w h
    size=$(get_screen_size)
    w=$(echo "$size" | cut -dx -f1)
    h=$(echo "$size" | cut -dx -f2)
    echo "$w $h"
}

# 清理后台进程（整棵进程树）
cleanup_bg() {
    if [ -n "$BG_PID" ] && kill -0 "$BG_PID" 2>/dev/null; then
        pkill -P "$BG_PID" 2>/dev/null || true
        kill "$BG_PID" 2>/dev/null || true
        wait "$BG_PID" 2>/dev/null || true
    fi
}
trap cleanup_bg EXIT

# 等待 trace 开始后再执行操作
wait_then_do() {
    local delay_sec="$1"
    shift
    (
        sleep "$delay_sec"
        "$@"
    ) &
    BG_PID=$!
}

# ──────────────────────────────────────────
# 场景执行
# ──────────────────────────────────────────
run_scenario() {
    local scenario="$1"
    local screen_size screen_w screen_h cx cy
    screen_size=$(get_screen_size)
    if [ -z "$screen_size" ]; then
        echo "错误: 无法获取屏幕尺寸"
        exit 1
    fi
    screen_w=$(echo "$screen_size" | cut -dx -f1)
    screen_h=$(echo "$screen_size" | cut -dx -f2)
    cx=$((screen_w / 2))
    cy=$((screen_h / 2))
    # 日历网格大致区域（屏幕中间偏上）
    local grid_y=$((screen_h * 35 / 100))
    # FAB 按钮大致位置（右下）
    local fab_x=$((screen_w * 85 / 100))
    local fab_y=$((screen_h * 85 / 100))
    # BottomCard 拖拽区域（屏幕下半部分）
    local bottom_y=$((screen_h * 70 / 100))

    case "$scenario" in
        month_browse)
            SCENARIO_NAME="月视图滑动翻页"
            SCENARIO_DESC="在月视图中快速左右滑动翻页，测试 CalendarPager 重组和绘制性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true || true
            sleep 1
            echo "  → 自动执行：trace 开始后将连续左右滑动翻页"
            wait_then_do 2 bash -c "
                for i in 1 2 3 4 5 6 7 8; do
                    adb shell input swipe $((screen_w*80/100)) $grid_y $((screen_w*20/100)) $grid_y 200
                    sleep 0.4
                    adb shell input swipe $((screen_w*20/100)) $grid_y $((screen_w*80/100)) $grid_y 200
                    sleep 0.4
                done
            "
            ;;

        date_select)
            SCENARIO_NAME="点击日期选择"
            SCENARIO_DESC="依次点击日历中不同位置的日期，测试 DayCell 重组和 AnimatedWebp 动画性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将依次点击日历中不同位置的日期"
            # 日历网格大致区域：7列 x 最多6行
            local col_w=$((screen_w / 7))
            local row_h=$(((screen_h * 40 / 100) / 6))
            local grid_top=$((screen_h * 22 / 100))
            wait_then_do 2 bash -c "
                for row in 1 2 3 4 5; do
                    for col in 1 3 5 6; do
                        local x=$((col_w * col + col_w / 2))
                        local y=$((grid_top + row_h * row + row_h / 2))
                        adb shell input tap \$x \$y
                        sleep 0.6
                    done
                done
            "
            ;;

        collapse_expand)
            SCENARIO_NAME="折叠/展开 月视图↔周视图"
            SCENARIO_DESC="拖拽 BottomCard 上下切换月视图和周视图，测试折叠动画和布局变化性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将自动拖拽 BottomCard 上下切换"
            wait_then_do 2 bash -c "
                for i in 1 2 3 4 5; do
                    # 向上拖拽（月→周）
                    adb shell input swipe $cx $bottom_y $cx $((screen_h*40/100)) 400
                    sleep 0.8
                    # 向下拖拽（周→月）
                    adb shell input swipe $cx $((screen_h*40/100)) $cx $bottom_y 400
                    sleep 0.8
                done
            "
            ;;

        year_view)
            SCENARIO_NAME="年视图切换+年份滑动"
            SCENARIO_DESC="打开年视图后在年份间左右滑动，测试 YearGridView 重组和 AnimatedContent 转场性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            # 打开年视图（点击 FAB → 年视图）
            adb shell input tap "$fab_x" "$fab_y"
            sleep 0.5
            # 点击"年视图"菜单项（在 FAB 上方约 100px）
            adb shell input tap "$fab_x" $((fab_y - 120))
            sleep 0.8
            echo "  → 自动执行：已打开年视图，trace 开始后将滑动浏览年份"
            wait_then_do 2 bash -c "
                for i in 1 2 3 4; do
                    adb shell input swipe $((screen_w*80/100)) $cy $((screen_w*20/100)) $cy 200
                    sleep 0.5
                    adb shell input swipe $((screen_w*20/100)) $cy $((screen_w*80/100)) $cy 200
                    sleep 0.5
                done
            "
            ;;

        year_select)
            SCENARIO_NAME="年视图中选择月份"
            SCENARIO_DESC="在年视图中点击不同月份返回月视图，测试 MonthView→YearView→MonthView 完整转场链"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            # 打开年视图
            adb shell input tap "$fab_x" "$fab_y"
            sleep 0.5
            adb shell input tap "$fab_x" $((fab_y - 120))
            sleep 0.8
            echo "  → 自动执行：trace 开始后将依次点击不同的迷你月份"
            wait_then_do 2 bash -c "
                # 点击第1行不同月份（大致坐标）
                adb shell input tap $((screen_w*20/100)) $((screen_h*25/100))
                sleep 1.0
                # 重新打开年视图
                adb shell input tap $fab_x $fab_y
                sleep 0.3
                adb shell input tap $fab_x $((fab_y - 120))
                sleep 0.5
                adb shell input tap $((screen_w*50/100)) $((screen_h*25/100))
                sleep 1.0
                # 再试一个
                adb shell input tap $fab_x $fab_y
                sleep 0.3
                adb shell input tap $fab_x $((fab_y - 120))
                sleep 0.5
                adb shell input tap $((screen_w*80/100)) $((screen_h*50/100))
                sleep 1.0
            "
            ;;

        today_jump)
            SCENARIO_NAME="跳转到今天"
            SCENARIO_DESC="先翻到非当月，然后点击"今天"按钮跳转，测试自动翻页和选中动画"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            # 先翻几页离开当月
            adb shell input swipe $((screen_w*20/100)) $grid_y $((screen_w*80/100)) $grid_y 200
            sleep 0.5
            adb shell input swipe $((screen_w*20/100)) $grid_y $((screen_w*80/100)) $grid_y 200
            sleep 0.5
            echo "  → 自动执行：trace 开始后将点击"今天"按钮"
            # "今天"按钮大致在左上角
            wait_then_do 2 bash -c "
                adb shell input tap $((screen_w*15/100)) $((screen_h*12/100))
                sleep 2.0
                adb shell input swipe $((screen_w*20/100)) $grid_y $((screen_w*80/100)) $grid_y 200
                sleep 0.5
                adb shell input tap $((screen_w*15/100)) $((screen_h*12/100))
            "
            ;;

        menu_toggle)
            SCENARIO_NAME="FAB 菜单打开关闭"
            SCENARIO_DESC="反复打开/关闭 FAB 菜单，测试 AnimatedVisibility 缩放动画性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将反复点击 FAB"
            wait_then_do 2 bash -c "
                for i in 1 2 3 4 5 6 7 8 9 10; do
                    adb shell input tap $fab_x $fab_y
                    sleep 0.5
                done
            "
            ;;

        legal_holiday)
            SCENARIO_NAME="显示调休切换"
            SCENARIO_DESC="切换"显示调休"开关，测试 DayCell 大规模重组和 staggered 动画性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将反复切换"显示调休""
            wait_then_do 2 bash -c "
                for i in 1 2 3 4; do
                    # 打开菜单
                    adb shell input tap $fab_x $fab_y
                    sleep 0.4
                    # 点击"显示调休"（在 FAB 上方约 200px）
                    adb shell input tap $fab_x $((fab_y - 200))
                    sleep 1.0
                done
            "
            ;;

        cross_month)
            SCENARIO_NAME="跨月日期选择"
            SCENARIO_DESC="点击上月或下月的灰色日期，测试跨月自动跳转和 pager 同步性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将点击首行左侧（上月）和末行右侧（下月）的灰色日期"
            local col_w=$((screen_w / 7))
            local row_h=$(((screen_h * 40 / 100) / 6))
            local grid_top=$((screen_h * 22 / 100))
            wait_then_do 2 bash -c "
                # 点击首行左侧（上月灰色日期，第1列）
                adb shell input tap $((col_w / 2)) $((grid_top + row_h / 2))
                sleep 1.5
                # 点击末行右侧（下月灰色日期，第7列）
                adb shell input tap $((screen_w - col_w / 2)) $((grid_top + row_h * 5 + row_h / 2))
                sleep 1.5
                # 再点击首行右侧（上月灰色日期，第2列）
                adb shell input tap $((col_w + col_w / 2)) $((grid_top + row_h / 2))
                sleep 1.5
                # 再点击末行左侧（下月灰色日期，第6列）
                adb shell input tap $((screen_w - col_w - col_w / 2)) $((grid_top + row_h * 5 + row_h / 2))
                sleep 1.5
            "
            ;;

        tools)
            SCENARIO_NAME="工具页面"
            SCENARIO_DESC="打开工具页面，测试 Activity 转场动画和 ToolsScreen 渲染性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${TOOLS_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：工具页面已打开，trace 期间保持静态"
            ;;

        date_checker)
            SCENARIO_NAME="日期检查器"
            SCENARIO_DESC="在日期检查器中进行添加行、输入天数、滑动删除等操作，测试复杂表单性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${DATECHECKER_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将自动添加行、输入天数、滑动删除"
            wait_then_do 2 bash -c "
                # 1. 点击 FAB 添加新行
                adb shell input tap $fab_x $fab_y
                sleep 1.0

                # 2. 点击新行的天数输入框（大致在屏幕中间偏下）
                adb shell input tap $((screen_w*35/100)) $((screen_h*75/100))
                sleep 0.5
                adb shell input text '90'
                sleep 0.8

                # 3. 点击空白处收起键盘（点击列表区域上方）
                adb shell input tap $cx $((screen_h*20/100))
                sleep 0.8

                # 4. 再添加一行
                adb shell input tap $fab_x $fab_y
                sleep 1.0

                # 5. 向左滑动第2行删除（从右向左滑）
                adb shell input swipe $((screen_w*80/100)) $((screen_h*65/100)) $((screen_w*20/100)) $((screen_h*65/100)) 300
                sleep 1.5

                # 6. 点击生产日期卡片（顶部区域）
                adb shell input tap $cx $((screen_h*18/100))
                sleep 0.8
                # 点击日期选择器的"确定"按钮（大致在右下角）
                adb shell input tap $((screen_w*80/100)) $((screen_h*90/100))
                sleep 1.0
            "
            ;;

        about)
            SCENARIO_NAME="关于页面"
            SCENARIO_DESC="打开关于页面，测试 Activity 转场和 AboutScreen 渲染性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${ABOUT_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：关于页面已打开，trace 期间保持静态"
            ;;

        licenses)
            SCENARIO_NAME="开源许可列表"
            SCENARIO_DESC="打开开源许可页面并上下滑动，测试 LicensesScreen 列表滑动性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${LICENSES_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：许可页面已打开，trace 开始后将上下滑动"
            wait_then_do 2 bash -c "
                for i in 1 2 3 4 5; do
                    adb shell input swipe $cx $((screen_h*70/100)) $cx $((screen_h*30/100)) 300
                    sleep 0.5
                    adb shell input swipe $cx $((screen_h*30/100)) $cx $((screen_h*70/100)) 300
                    sleep 0.5
                done
            "
            ;;

        full_flow)
            SCENARIO_NAME="完整用户流程"
            SCENARIO_DESC="自动串联所有主要交互的完整流程，综合评估整体性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true
            sleep 1
            echo "  → 自动执行：trace 开始后将按顺序自动执行所有主要交互"
            local col_w=$((screen_w / 7))
            local row_h=$(((screen_h * 40 / 100) / 6))
            local grid_top=$((screen_h * 22 / 100))
            wait_then_do 2 bash -c "
                # 1. 左右滑动翻页 2 次
                adb shell input swipe $((screen_w*80/100)) $grid_y $((screen_w*20/100)) $grid_y 200
                sleep 0.8
                adb shell input swipe $((screen_w*20/100)) $grid_y $((screen_w*80/100)) $grid_y 200
                sleep 0.8

                # 2. 点击几个不同日期
                adb shell input tap $((col_w * 2)) $((grid_top + row_h * 2 + row_h / 2))
                sleep 0.6
                adb shell input tap $((col_w * 5)) $((grid_top + row_h * 3 + row_h / 2))
                sleep 0.6
                adb shell input tap $((col_w * 3)) $((grid_top + row_h * 4 + row_h / 2))
                sleep 0.6

                # 3. 向上拖拽 BottomCard 切换到周视图
                adb shell input swipe $cx $((screen_h*70/100)) $cx $((screen_h*40/100)) 400
                sleep 1.0

                # 4. 在周视图中左右滑动
                adb shell input swipe $((screen_w*80/100)) $grid_y $((screen_w*20/100)) $grid_y 200
                sleep 0.8
                adb shell input swipe $((screen_w*20/100)) $grid_y $((screen_w*80/100)) $grid_y 200
                sleep 0.8

                # 5. 向下拖拽展开回月视图
                adb shell input swipe $cx $((screen_h*40/100)) $cx $((screen_h*70/100)) 400
                sleep 1.0

                # 6. 点击 FAB 打开菜单
                adb shell input tap $fab_x $fab_y
                sleep 0.5
                # 7. 点击"年视图"
                adb shell input tap $fab_x $((fab_y - 120))
                sleep 1.5

                # 8. 年视图中滑动浏览年份
                adb shell input swipe $((screen_w*80/100)) $cy $((screen_w*20/100)) $cy 200
                sleep 0.8
                adb shell input swipe $((screen_w*20/100)) $cy $((screen_w*80/100)) $cy 200
                sleep 0.8

                # 9. 点击一个月份返回
                adb shell input tap $((screen_w*50/100)) $((screen_h*35/100))
                sleep 1.5

                # 10. 点击 FAB → 显示调休（切换一次）
                adb shell input tap $fab_x $fab_y
                sleep 0.4
                adb shell input tap $fab_x $((fab_y - 200))
                sleep 1.0

                # 11. 点击"今天"按钮
                adb shell input tap $((screen_w*15/100)) $((screen_h*12/100))
                sleep 1.5

                # 12. 打开工具页面
                adb shell am start -n ${PACKAGE}/${TOOLS_ACTIVITY}
                sleep 1.5

                # 13. 打开日期检查器
                adb shell input tap $((screen_w*50/100)) $((screen_h*25/100))
                sleep 1.5

                # 14. 日期检查器：添加一行 + 输入天数
                adb shell input tap $fab_x $fab_y
                sleep 1.0
                adb shell input tap $((screen_w*35/100)) $((screen_h*75/100))
                sleep 0.5
                adb shell input text '90'
                sleep 0.8
                adb shell input tap $cx $((screen_h*20/100))
                sleep 0.8

                # 15. 返回 → 关于
                adb shell input keyevent 4
                sleep 1.0
                adb shell input keyevent 4
                sleep 1.0
                adb shell am start -n ${PACKAGE}/${ABOUT_ACTIVITY}
                sleep 1.5

                # 16. 点击"开放源代码许可"
                adb shell input tap $cx $((screen_h*65/100))
                sleep 1.5

                # 17. 滑动许可列表
                adb shell input swipe $cx $((screen_h*70/100)) $cx $((screen_h*30/100)) 300
                sleep 0.8
                adb shell input swipe $cx $((screen_h*30/100)) $cx $((screen_h*70/100)) 300
                sleep 0.8

                # 18. 返回主页面
                adb shell input keyevent 4
                sleep 1.0
                adb shell input keyevent 4
                sleep 1.0
                adb shell am start -n ${PACKAGE}/${MAIN_ACTIVITY}
                sleep 1.0
            "
            ;;

        all_activities)
            SCENARIO_NAME="遍历所有 Activity"
            SCENARIO_DESC="自动依次打开所有 Activity，测试 Activity 启动和转场动画性能"
            echo ""
            echo "[场景] ${SCENARIO_NAME}"
            echo "  ${SCENARIO_DESC}"
            echo ""
            echo "  → 自动执行：trace 开始后将依次打开所有页面"
            wait_then_do 2 bash -c "
                sleep 1
                adb shell am start -n ${PACKAGE}/${MAIN_ACTIVITY}
                sleep 1.5
                adb shell am start -n ${PACKAGE}/${TOOLS_ACTIVITY}
                sleep 1.5
                adb shell am start -n ${PACKAGE}/${ABOUT_ACTIVITY}
                sleep 1.5
                adb shell am start -n ${PACKAGE}/${LICENSES_ACTIVITY}
                sleep 1.5
                adb shell am start -n ${PACKAGE}/${DATECHECKER_ACTIVITY}
                sleep 1.5
                adb shell am start -n ${PACKAGE}/${MAIN_ACTIVITY}
                sleep 1.0
            "
            ;;

        *)
            echo "错误: 未知场景: $scenario"
            echo "使用 --list-scenarios 查看可用场景"
            exit 1
            ;;
    esac
}

# ──────────────────────────────────────────
# 主流程
# ──────────────────────────────────────────

DURATION_MS=$((DURATION_SEC * 1000))
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# 场景后缀
SCENARIO_SUFFIX=""
if [ -n "$SCENARIO" ]; then
    SCENARIO_SUFFIX="_${SCENARIO}"
fi

echo "========================================"
echo "  YaYa 性能追踪"
echo "  包名: ${PACKAGE}"
echo "  时长: ${DURATION_SEC}s"
echo "  构建: $([ "$USE_TRACE_BUILD" = true ] && echo "trace (release + trace)" || echo "debug")"
if [ -n "$SCENARIO" ]; then
    echo "  场景: ${SCENARIO}"
fi
echo "  输出: ${LOGS_DIR}/"
echo "========================================"

# 创建 logs 目录
mkdir -p "${LOGS_DIR}"

# 检查 adb
if ! command -v adb &>/dev/null; then
    echo "错误: adb 未找到。请确保 Android SDK 的 platform-tools 在 PATH 中。"
    exit 1
fi

# 检查设备连接
DEVICE_COUNT=$(adb devices | grep -c "device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "错误: 没有已连接的 Android 设备。"
    exit 1
fi
if [ "$DEVICE_COUNT" -gt 1 ]; then
    echo "警告: 检测到多个设备，将使用默认设备。"
fi

# 检查应用是否已安装
if ! adb shell pm list packages | grep -q "${PACKAGE}"; then
    echo "错误: 应用 ${PACKAGE} 未安装。请先运行 ./gradlew :app:installDebug"
    if [ "$USE_TRACE_BUILD" = true ]; then
        echo "       或使用 ./gradlew :app:installTrace 安装 trace 构建"
    fi
    exit 1
fi

# ──────────────────────────────────────────
# 批量录制所有自动场景
# ──────────────────────────────────────────
run_all_scenarios() {
    local script="$0"
    local per_scene_duration="${DURATION_SEC}"
    # --all 模式下默认每个场景 10 秒
    if [ "$per_scene_duration" -eq 8 ]; then
        per_scene_duration=10
    fi

    local auto_scenarios=(
        month_browse
        date_select
        collapse_expand
        year_view
        year_select
        today_jump
        menu_toggle
        legal_holiday
        cross_month
        tools
        date_checker
        about
        licenses
        all_activities
        full_flow
    )
    local total=${#auto_scenarios[@]}
    local build_flag=""
    [ "$USE_TRACE_BUILD" = true ] && build_flag="--trace"

    echo ""
    echo "========================================"
    echo "  批量录制模式"
    echo "  共 ${total} 个自动场景"
    echo "  每个场景 ${per_scene_duration} 秒"
    echo "========================================"
    echo ""

    local i=0
    for s in "${auto_scenarios[@]}"; do
        i=$((i + 1))
        echo ""
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        echo "  [${i}/${total}] 录制场景: ${s}"
        echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
        "$script" --scenario "$s" $build_flag "$per_scene_duration"
        if [ "$i" -lt "$total" ]; then
            echo ""
            echo "  休息 2 秒，准备下一个场景..."
            sleep 2
        fi
    done

    # 生成汇总报告
    generate_summary_report "$total" "$per_scene_duration"
}

# 生成批量录制汇总报告
generate_summary_report() {
    local total="$1"
    local duration="$2"
    local summary_time
    summary_time=$(date +%Y%m%d_%H%M%S)
    local summary_file="${LOGS_DIR}/report_${summary_time}_ALL_SUMMARY.md"

    echo ""
    echo "========================================"
    echo "  生成批量录制汇总报告"
    echo "========================================"

    cat > "$summary_file" <<EOF
# YaYa 批量性能追踪汇总报告

**时间:** $(date '+%Y-%m-%d %H:%M:%S')
**设备:** ${DEVICE_MODEL} (Android ${ANDROID_VERSION})
**应用版本:** ${APP_VERSION}
**构建类型:** $([ "$USE_TRACE_BUILD" = true ] && echo "trace" || echo "debug")
**场景数:** ${total}
**每场景时长:** ${duration}s

## 各场景帧率对比

| 场景 | 总帧数 | 掉帧数 | 掉帧比例 | P50 | P90 | P99 | Slow Draw | Total PSS |
|------|--------|--------|---------|-----|-----|-----|-----------|-----------|
EOF

    for s in month_browse date_select collapse_expand year_view year_select today_jump menu_toggle legal_holiday cross_month tools date_checker about licenses all_activities full_flow; do
        local report
        report=$(ls -t "${LOGS_DIR}"/report_*"_${s}".md 2>/dev/null | head -1)
        if [ -n "$report" ]; then
            local tf jf jp p50 p90 p99 sd tp
            tf=$(awk '/Total frames rendered:/{print $4; exit}' "${report}")
            jf=$(awk '/掉帧数/{getline; print $2; exit}' "${report}")
            jp=$(awk '/掉帧比例/{getline; print $2; exit}' "${report}")
            p50=$(awk '/50th percentile/{getline; print $2; exit}' "${report}")
            p90=$(awk '/90th percentile/{getline; print $2; exit}' "${report}")
            p99=$(awk '/99th percentile/{getline; print $2; exit}' "${report}")
            sd=$(awk '/Slow draw commands/{getline; print $2; exit}' "${report}")
            tp=$(awk '/Total PSS/{getline; print $2; exit}' "${report}")
            printf "| %s | %s | %s | %s | %s | %s | %s | %s | %s |\n" "$s" "${tf:-N/A}" "${jf:-N/A}" "${jp:-N/A}" "${p50:-N/A}" "${p90:-N/A}" "${p99:-N/A}" "${sd:-N/A}" "${tp:-N/A}" >> "$summary_file"
        fi
    done

    cat >> "$summary_file" <<EOF

## 分析建议

1. **掉帧比例最高**的场景是性能瓶颈所在，优先优化
2. **Slow draw commands** 高的场景说明 GPU 绘制是瓶颈
3. **P99 延迟高**的场景说明存在偶发性卡顿
4. 对比各场景的 **Total PSS**，观察内存波动
5. **full_flow** 综合场景的数据可反映真实用户使用时的整体体验

## 文件清单

EOF

    for s in month_browse date_select collapse_expand year_view year_select today_jump menu_toggle legal_holiday cross_month tools date_checker about licenses all_activities full_flow; do
        local trace report
        trace=$(ls -t "${LOGS_DIR}"/trace_*"_${s}".perfetto-trace 2>/dev/null | head -1)
        report=$(ls -t "${LOGS_DIR}"/report_*"_${s}".md 2>/dev/null | head -1)
        if [ -n "$trace" ]; then
            echo "- \`$(basename "$trace")\` + \`$(basename "$report")\` — ${s}" >> "$summary_file"
        fi
    done

    echo "" >> "$summary_file"
    echo "---" >> "$summary_file"
    echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "$summary_file"

    echo ""
    echo "========================================"
    echo "  批量录制完成！"
    echo "========================================"
    echo ""
    echo "汇总报告: ${summary_file}"
    echo ""
}

# ──────────────────────────────────────────
# 如果是 --all 模式，直接执行批量录制
# ──────────────────────────────────────────
if [ "$RUN_ALL" = true ]; then
    run_all_scenarios
    exit 0
fi

# ──────────────────────────────────────────
# 场景准备
# ──────────────────────────────────────────
if [ -n "$SCENARIO" ]; then
    run_scenario "$SCENARIO"
else
    # 默认场景：仅启动应用
    if [ "$NO_LAUNCH" = false ]; then
        echo ""
        echo "[1/5] 启动应用..."
        adb shell am start -n "${PACKAGE}/${MAIN_ACTIVITY}" >/dev/null 2>&1 || true || true
        sleep 2
    else
        echo ""
        echo "[1/5] 跳过启动 (--no-launch)"
    fi
fi

# 如果不是自动场景，给用户一点准备时间
if [ -n "$SCENARIO" ] && [ "$SCENARIO" = "full_flow" ] || [ "$SCENARIO" = "date_select" ] || [ "$SCENARIO" = "cross_month" ] || [ "$SCENARIO" = "date_checker" ]; then
    echo ""
    echo "准备开始 trace，请在倒计时结束后立即操作..."
    for i in 3 2 1; do
        echo "  $i..."
        sleep 1
    done
fi

# ──────────────────────────────────────────
# 抓取 Perfetto trace
# ──────────────────────────────────────────
echo ""
echo "[2/5] 抓取 Perfetto trace (${DURATION_SEC}s)..."
if [ -n "$SCENARIO" ]; then
    echo "      场景: ${SCENARIO_NAME}"
    echo "      自动操作已启动，请按提示完成手动操作..."
else
    echo "      请在设备上操作应用，trace 正在记录..."
fi

TRACE_FILE="/data/misc/perfetto-traces/yaya_${TIMESTAMP}${SCENARIO_SUFFIX}.perfetto-trace"
LOCAL_TRACE="${LOGS_DIR}/trace_${TIMESTAMP}${SCENARIO_SUFFIX}.perfetto-trace"
LOCAL_CONFIG="${LOGS_DIR}/.perfetto_config_${TIMESTAMP}${SCENARIO_SUFFIX}.txt"
DEVICE_CONFIG="/data/misc/perfetto-configs/yaya_config_${TIMESTAMP}${SCENARIO_SUFFIX}.txt"

# 生成本地配置文件，然后 push 到设备
cat > "${LOCAL_CONFIG}" <<EOF
buffers {
  size_kb: 131072
  fill_policy: RING_BUFFER
}
buffers {
  size_kb: 4096
  fill_policy: RING_BUFFER
}
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "wm"
      atrace_categories: "am"
      atrace_categories: "input"
      atrace_categories: "sched"
      atrace_categories: "freq"
      atrace_categories: "idle"
      atrace_apps: "${PACKAGE}"
    }
  }
}
data_sources {
  config {
    name: "android.gpu.memory"
  }
}
data_sources {
  config {
    name: "android.surfaceflinger.frametimeline"
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 1
    process_stats_config {
      scan_all_processes_on_start: true
    }
  }
}
duration_ms: ${DURATION_MS}
EOF

adb push "${LOCAL_CONFIG}" "${DEVICE_CONFIG}" > /dev/null
rm -f "${LOCAL_CONFIG}"

# 运行 perfetto（前台阻塞，直到 duration_ms 结束）
adb shell "perfetto --txt -c ${DEVICE_CONFIG} -o ${TRACE_FILE}"

# 清理设备上的临时配置文件
adb shell "rm -f ${DEVICE_CONFIG}"

# 拉取 trace 文件
echo "      拉取 trace 文件..."
adb pull "${TRACE_FILE}" "${LOCAL_TRACE}"
adb shell "rm -f ${TRACE_FILE}" || true

# 等待后台操作完成
cleanup_bg

# 抓取帧统计
echo ""
echo "[3/5] 抓取帧统计..."
FRAMESTATS_FILE="${LOGS_DIR}/framestats_${TIMESTAMP}${SCENARIO_SUFFIX}.txt"
adb shell dumpsys gfxinfo "${PACKAGE}" framestats > "${FRAMESTATS_FILE}"

# 抓取内存信息
echo ""
echo "[4/5] 抓取内存信息..."
MEMINFO_FILE="${LOGS_DIR}/meminfo_${TIMESTAMP}${SCENARIO_SUFFIX}.txt"
adb shell dumpsys meminfo "${PACKAGE}" > "${MEMINFO_FILE}"

# ──────────────────────────────────────────
# 生成报告摘要
# ──────────────────────────────────────────
echo ""
echo "[5/5] 生成摘要..."
REPORT_FILE="${LOGS_DIR}/report_${TIMESTAMP}${SCENARIO_SUFFIX}.md"

# 计算帧率相关数据
FRAME_COUNT=$(grep -c "FrameTimeline" "${FRAMESTATS_FILE}" 2>/dev/null || echo "0")

# 从 gfxinfo 提取关键指标（取第一个匹配，即整体统计而非 per-window）
TOTAL_FRAMES=$(awk '/Total frames rendered:/{print $4; exit}' "${FRAMESTATS_FILE}")
JANKY_FRAMES=$(awk '/Janky frames:/{print $3; exit}' "${FRAMESTATS_FILE}")
JANKY_PERCENT=$(awk '/Janky frames:/{start=index($0,"(")+1; end=index($0,")"); print substr($0,start,end-start); exit}' "${FRAMESTATS_FILE}")
P50=$(awk '/50th percentile:/{print $3; exit}' "${FRAMESTATS_FILE}")
P90=$(awk '/90th percentile:/{print $3; exit}' "${FRAMESTATS_FILE}")
P99=$(awk '/99th percentile:/{print $3; exit}' "${FRAMESTATS_FILE}")
SLOW_UI=$(awk '/Number Slow UI thread:/{print $NF; exit}' "${FRAMESTATS_FILE}")
SLOW_DRAW=$(awk '/Number Slow issue draw commands:/{print $NF; exit}' "${FRAMESTATS_FILE}")
HIGH_INPUT=$(awk '/Number High input latency:/{print $NF; exit}' "${FRAMESTATS_FILE}")

# 获取应用版本
APP_VERSION=$(adb shell dumpsys package "${PACKAGE}" | grep versionName | head -1 | awk '{print $1}' | cut -d= -f2 2>/dev/null || echo "unknown")

# 获取设备信息
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VERSION=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')

# 获取内存摘要
TOTAL_PSS=$(awk '/TOTAL PSS:/{print $3; exit}' "${MEMINFO_FILE}")
JAVA_HEAP=$(awk '/^ *Java Heap:/{print $3; exit}' "${MEMINFO_FILE}")
NATIVE_HEAP=$(awk '/^ *Native Heap:/{print $3; exit}' "${MEMINFO_FILE}")
GRAPHICS=$(awk '/^ *Graphics:/{print $2; exit}' "${MEMINFO_FILE}")

cat > "${REPORT_FILE}" <<EOF
# YaYa 性能追踪报告

**时间:** $(date '+%Y-%m-%d %H:%M:%S')
**设备:** ${DEVICE_MODEL} (Android ${ANDROID_VERSION})
**应用版本:** ${APP_VERSION}
**构建类型:** $([ "$USE_TRACE_BUILD" = true ] && echo "trace" || echo "debug")
**追踪时长:** ${DURATION_SEC}s
EOF

# 场景信息
if [ -n "$SCENARIO" ]; then
    cat >> "${REPORT_FILE}" <<EOF
**场景:** ${SCENARIO}
**场景名称:** ${SCENARIO_NAME}
**场景说明:** ${SCENARIO_DESC}
EOF
fi

cat >> "${REPORT_FILE}" <<EOF

## 文件清单

| 文件 | 说明 |
|------|------|
| \`trace_${TIMESTAMP}${SCENARIO_SUFFIX}.perfetto-trace\` | Perfetto trace（在 https://ui.perfetto.dev 中打开） |
| \`framestats_${TIMESTAMP}${SCENARIO_SUFFIX}.txt\` | GPU 帧统计 |
| \`meminfo_${TIMESTAMP}${SCENARIO_SUFFIX}.txt\` | 内存快照 |
| \`report_${TIMESTAMP}${SCENARIO_SUFFIX}.md\` | 本报告 |

## 帧率摘要

| 指标 | 数值 |
|------|------|
| 总渲染帧数 | ${TOTAL_FRAMES} |
| 掉帧数 | ${JANKY_FRAMES} |
| 掉帧比例 | ${JANKY_PERCENT} |
| 50th percentile | ${P50} |
| 90th percentile | ${P90} |
| 99th percentile | ${P99} |
| Slow UI thread | ${SLOW_UI} |
| Slow draw commands | ${SLOW_DRAW} |
| High input latency | ${HIGH_INPUT} |

## 内存摘要

| 指标 | 数值 (KB) |
|------|----------|
| Total PSS | ${TOTAL_PSS} |
| Java Heap | ${JAVA_HEAP} |
| Native Heap | ${NATIVE_HEAP} |
| Graphics | ${GRAPHICS} |
EOF

# 场景特有分析指南
if [ -n "$SCENARIO" ]; then
    cat >> "${REPORT_FILE}" <<EOF

## 场景分析指南

本报告对应场景: **${SCENARIO_NAME}**

### 在 Perfetto UI 中重点查看：
EOF

    case "$SCENARIO" in
        month_browse)
            cat >> "${REPORT_FILE}" <<EOF
- \`CalendarPager:Page:*\` — 各月分页重组耗时分布
- \`MonthView:Compose\` — 月视图整体重组频率
- \`getMonthDays:*\` — 月份网格计算耗时
- 对比不同月份页面的 \`max_ms\`，找出重组最慢的月份
EOF
            ;;
        date_select)
            cat >> "${REPORT_FILE}" <<EOF
- \`DayCell\` — 单个日期单元格重组（通过 transition label）
- \`MonthView:Compose\` — 日期选中触发的整页重组
- \`AnimatedWebp\` 相关 trace（如果存在）— 选中动画
- 查找是否有连续多帧超过 16.67ms（选中动画期间）
EOF
            ;;
        collapse_expand)
            cat >> "${REPORT_FILE}" <<EOF
- \`VM:collapseProgress\` — 折叠动画状态更新频率和耗时
- \`CalendarPagerArea\` — 区域重组（月↔周切换时的布局变化）
- \`WeekPager:Page:*\` — 周视图出现时的重组
- 观察 \`collapseProgress\` 与帧数据的对应关系
EOF
            ;;
        year_view|year_select)
            cat >> "${REPORT_FILE}" <<EOF
- \`MonthView→YearView\` / \`YearView→MonthView\` — 转场 trace
- \`YearView:Compose\` — 年视图整体重组
- \`YearGridView:*\` — 年视图网格重组
- \`generateMiniMonthDays:*\` — 迷你月日期计算
- 对比转场前后 500ms 的帧率变化
EOF
            ;;
        menu_toggle)
            cat >> "${REPORT_FILE}" <<EOF
- 查找 AnimatedVisibility 相关的重组 slice
- \`MonthView:Compose\` — 菜单开关是否触发整页重组
- 观察菜单动画期间的帧率是否稳定
EOF
            ;;
        legal_holiday)
            cat >> "${REPORT_FILE}" <<EOF
- \`DayCell\` — 大规模重组（所有日期单元格可能同时重组）
- 查找 staggered 动画相关的 trace（delay=cellIndex*15）
- 对比开关前后的 \`MonthView:Compose\` 重组次数
EOF
            ;;
        tools|about|licenses|date_checker)
            cat >> "${REPORT_FILE}" <<EOF
- 查看对应 Activity 启动时的 Choreographer#doFrame 帧数据
- \`MonthView:Compose\`（如从主页面跳转）— 转场期间的重组
- Activity 转场动画期间的帧率稳定性
EOF
            ;;
        full_flow)
            cat >> "${REPORT_FILE}" <<EOF
- 分段分析：按时间轴对应不同操作阶段
- 标记出各阶段切换时的帧率变化
- \`MonthView→YearView\`、\`YearView→MonthView\` 等转场标记
- 整体评估哪些操作阶段掉帧最严重
EOF
            ;;
        all_activities)
            cat >> "${REPORT_FILE}" <<EOF
- 按时间分段，对应不同 Activity 启动阶段
- 比较各 Activity 启动时的首帧耗时
- 观察 Activity 转场动画（slide_in_right）期间的帧率
EOF
            ;;
        *)
            cat >> "${REPORT_FILE}" <<EOF
- 查看本场景相关的自定义 trace 标记
- 分析对应时间段的帧率数据
EOF
            ;;
    esac
fi

cat >> "${REPORT_FILE}" <<EOF

## 通用 Perfetto 分析指南

打开 [Perfetto UI](https://ui.perfetto.dev)，上传 trace 文件：

### 1. 查看 Compose 自定义标记
搜索以下 trace section：

**月视图相关：**
- \`MonthView:Compose\` — 月视图整体重组
- \`CalendarPagerArea\` — 日历分页器区域重组
- \`CalendarPager:Page\` — 月视图单页重组
- \`WeekPager:Page\` — 周视图单页重组
- \`getMonthDays:*\` — 月份网格计算

**年视图相关：**
- \`YearView:Compose\` — 年视图整体重组
- \`YearGridView:*\` — 年视图网格重组
- \`generateMiniMonthDays:*\` — 迷你月日期计算
- \`YearView:SelectMonth\` — 年视图选择月份

**转场动画：**
- \`MonthView→YearView\` — 月→年视图切换
- \`YearView→MonthView\` — 年→月视图切换
- \`VM:collapseProgress\` — 折叠动画状态更新

**单日单元格：**
- \`DayCell\` — 单个日期单元格（通过 transition label）

### 2. 分析帧率
在 \`framestats_${TIMESTAMP}${SCENARIO_SUFFIX}.txt\` 中查看：
- \`FrameTimeline\` 行 — 每帧的 CPU/GPU 耗时
- \`jank\` 标记 — 掉帧情况

### 3. 内存分析
在 \`meminfo_${TIMESTAMP}${SCENARIO_SUFFIX}.txt\` 中关注：
- \`TOTAL\` 行 — 应用总内存占用
- \`Graphics\` 行 — GPU 内存使用
- \`Native Heap\` 行 — 原生堆内存

## 基线对比方法

要对比优化前后的性能：

\`\`\`bash
# 1. 记录当前数据作为基线
$0 --scenario ${SCENARIO:-month_browse} --trace 15

# 2. 修改代码后重新编译
./gradlew :app:installTrace

# 3. 再次记录
$0 --scenario ${SCENARIO:-month_browse} --trace 15

# 4. 对比两个 report 中的帧率摘要表格
\`\`\`
EOF

echo ""
echo "========================================"
echo "  完成！"
echo "========================================"
echo ""
echo "输出文件:"
echo "  trace:      ${LOCAL_TRACE}"
echo "  framestats: ${FRAMESTATS_FILE}"
echo "  meminfo:    ${MEMINFO_FILE}"
echo "  report:     ${REPORT_FILE}"
echo ""
if [ -n "$SCENARIO" ]; then
    echo "场景: ${SCENARIO_NAME}"
    echo ""
fi
echo "下一步:"
echo "  1. 打开 https://ui.perfetto.dev"
echo "  2. 上传 trace_${TIMESTAMP}${SCENARIO_SUFFIX}.perfetto-trace"
if [ -n "$SCENARIO" ]; then
    echo "  3. 搜索场景相关的 trace 标记进行分析"
else
    echo "  3. 搜索 'MonthView→YearView' 查看转场 trace"
fi
echo ""
