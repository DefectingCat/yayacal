#!/bin/bash
#
# YaYa 性能追踪脚本
# 使用 Perfetto 抓取应用 trace，保存到 logs/ 目录
#
# 用法:
#   ./scripts/profile.sh              # 默认抓取 8 秒
#   ./scripts/profile.sh 15           # 抓取 15 秒
#   ./scripts/profile.sh --no-launch  # 不自动启动应用（应用已在运行）
#   ./scripts/profile.sh --trace      # 使用 trace 构建类型（保留自定义 trace 标记）
#

set -euo pipefail

PACKAGE="plus.rua.project"
ACTIVITY="plus.rua.project.MainActivity"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS_DIR="${PROJECT_ROOT}/logs"

# 解析参数
DURATION_SEC=8
NO_LAUNCH=false
USE_TRACE_BUILD=false

for arg in "$@"; do
  case "$arg" in
    --no-launch)
      NO_LAUNCH=true
      ;;
    --trace)
      USE_TRACE_BUILD=true
      ;;
    --help|-h)
      echo "用法: $0 [秒数] [--no-launch] [--trace]"
      echo ""
      echo "选项:"
      echo "  秒数         抓取时长（默认 8 秒）"
      echo "  --no-launch  不自动启动应用"
      echo "  --trace      使用 trace 构建（release 优化 + 保留 trace 标记）"
      echo "  --help       显示此帮助"
      exit 0
      ;;
    [0-9]*)
      DURATION_SEC="$arg"
      ;;
  esac
done

DURATION_MS=$((DURATION_SEC * 1000))
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

echo "========================================"
echo "  YaYa 性能追踪"
echo "  包名: ${PACKAGE}"
echo "  时长: ${DURATION_SEC}s"
echo "  构建: $([ "$USE_TRACE_BUILD" = true ] && echo "trace (release + trace)" || echo "debug")"
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

# 启动应用（如果未禁用）
if [ "$NO_LAUNCH" = false ]; then
  echo ""
  echo "[1/5] 启动应用..."
  adb shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null 2>&1 || true
  sleep 2
else
  echo ""
  echo "[1/5] 跳过启动 (--no-launch)"
fi

# 抓取 Perfetto trace
echo ""
echo "[2/5] 抓取 Perfetto trace (${DURATION_SEC}s)..."
echo "      请在设备上操作应用，trace 正在记录..."

TRACE_FILE="/data/misc/perfetto-traces/yaya_${TIMESTAMP}.perfetto-trace"
LOCAL_TRACE="${LOGS_DIR}/trace_${TIMESTAMP}.perfetto-trace"
LOCAL_CONFIG="${LOGS_DIR}/.perfetto_config_${TIMESTAMP}.txt"
DEVICE_CONFIG="/data/misc/perfetto-configs/yaya_config_${TIMESTAMP}.txt"

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

# 抓取帧统计
echo ""
echo "[3/5] 抓取帧统计..."
FRAMESTATS_FILE="${LOGS_DIR}/framestats_${TIMESTAMP}.txt"
adb shell dumpsys gfxinfo "${PACKAGE}" framestats > "${FRAMESTATS_FILE}"

# 抓取内存信息
echo ""
echo "[4/5] 抓取内存信息..."
MEMINFO_FILE="${LOGS_DIR}/meminfo_${TIMESTAMP}.txt"
adb shell dumpsys meminfo "${PACKAGE}" > "${MEMINFO_FILE}"

# 生成报告摘要
echo ""
echo "[5/5] 生成摘要..."
REPORT_FILE="${LOGS_DIR}/report_${TIMESTAMP}.md"

# 计算帧率相关数据
FRAME_COUNT=$(grep -c "FrameTimeline" "${FRAMESTATS_FILE}" 2>/dev/null || echo "0")

# 从 gfxinfo 提取关键指标
TOTAL_FRAMES=$(grep "Total frames rendered:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $4}' || echo "N/A")
JANKY_FRAMES=$(grep "Janky frames:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $3}' || echo "N/A")
JANKY_PERCENT=$(grep "Janky frames:" "${FRAMESTATS_FILE}" | tail -1 | grep -oP '\(\K[^)]+' || echo "N/A")
P50=$(grep "50th percentile:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $3}' || echo "N/A")
P90=$(grep "90th percentile:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $3}' || echo "N/A")
P99=$(grep "99th percentile:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $3}' || echo "N/A")
SLOW_UI=$(grep "Number Slow UI thread:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $4}' || echo "N/A")
SLOW_DRAW=$(grep "Number Slow issue draw commands:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $5}' || echo "N/A")
HIGH_INPUT=$(grep "Number High input latency:" "${FRAMESTATS_FILE}" | tail -1 | awk '{print $4}' || echo "N/A")

# 获取应用版本
APP_VERSION=$(adb shell dumpsys package "${PACKAGE}" | grep versionName | head -1 | awk '{print $1}' | cut -d= -f2 2>/dev/null || echo "unknown")

# 获取设备信息
DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VERSION=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')

# 获取内存摘要
TOTAL_PSS=$(grep "TOTAL PSS:" "${MEMINFO_FILE}" | awk '{print $3}' || echo "N/A")
JAVA_HEAP=$(grep "Java Heap:" "${MEMINFO_FILE}" | head -1 | awk '{print $2}' || echo "N/A")
NATIVE_HEAP=$(grep "Native Heap:" "${MEMINFO_FILE}" | head -1 | awk '{print $2}' || echo "N/A")
GRAPHICS=$(grep "Graphics:" "${MEMINFO_FILE}" | head -1 | awk '{print $2}' || echo "N/A")

cat > "${REPORT_FILE}" <<EOF
# YaYa 性能追踪报告

**时间:** $(date '+%Y-%m-%d %H:%M:%S')
**设备:** ${DEVICE_MODEL} (Android ${ANDROID_VERSION})
**应用版本:** ${APP_VERSION}
**构建类型:** $([ "$USE_TRACE_BUILD" = true ] && echo "trace" || echo "debug")
**追踪时长:** ${DURATION_SEC}s

## 文件清单

| 文件 | 说明 |
|------|------|
| \`trace_${TIMESTAMP}.perfetto-trace\` | Perfetto trace（在 https://ui.perfetto.dev 中打开） |
| \`framestats_${TIMESTAMP}.txt\` | GPU 帧统计 |
| \`meminfo_${TIMESTAMP}.txt\` | 内存快照 |
| \`report_${TIMESTAMP}.md\` | 本报告 |

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

## Perfetto 分析指南

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
在 \`framestats_${TIMESTAMP}.txt\` 中查看：
- \`FrameTimeline\` 行 — 每帧的 CPU/GPU 耗时
- \`jank\` 标记 — 掉帧情况

### 3. 内存分析
在 \`meminfo_${TIMESTAMP}.txt\` 中关注：
- \`TOTAL\` 行 — 应用总内存占用
- \`Graphics\` 行 — GPU 内存使用
- \`Native Heap\` 行 — 原生堆内存

### 4. 年月视图切换专项分析

在 Perfetto 中按以下步骤分析转场性能：

1. 找到 \`MonthView→YearView\` 或 \`YearView→MonthView\` 标记
2. 查看标记前后 500ms 的帧数据：
   - 查找超过 16.67ms 的帧（Choreographer#doFrame）
   - 检查是否有连续多帧超过预算
3. 同时搜索 \`MonthView:Compose\` 和 \`YearView:Compose\`，观察重组重叠情况
4. 查看 \`YearGridView:*\` 的耗时，年视图 12 个月网格的计算和绘制成本

## 基线对比方法

要对比优化前后的性能：

\`\`\`bash
# 1. 记录当前数据作为基线
./scripts/profile.sh --trace 15

# 2. 修改代码后重新编译
./gradlew :app:installTrace

# 3. 再次记录
./scripts/profile.sh --trace 15

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
echo "下一步:"
echo "  1. 打开 https://ui.perfetto.dev"
echo "  2. 上传 trace_${TIMESTAMP}.perfetto-trace"
echo "  3. 搜索 'MonthView→YearView' 查看转场 trace"
echo ""
