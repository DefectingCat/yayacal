#!/bin/bash
#
# YaYa Perfetto Trace 分析脚本
# 使用 trace_processor_shell 对 .perfetto-trace 文件进行 SQL 查询分析
#
# 用法:
#   ./scripts/analyze-trace.sh                           # 分析 logs/ 下最新的 trace
#   ./scripts/analyze-trace.sh logs/trace_xxx.perfetto-trace  # 分析指定文件
#

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS_DIR="${PROJECT_ROOT}/logs"
TP_SHELL="${HOME}/.local/bin/trace_processor_shell"
APP_THREAD="lus.rua.project"  # plus.rua.project 的截断名

# --- 参数解析 ---
TRACE_FILE=""
if [ $# -ge 1 ] && [ -f "$1" ]; then
  TRACE_FILE="$1"
fi

# 未指定文件时，取 logs/ 下最新的 .perfetto-trace
if [ -z "$TRACE_FILE" ]; then
  TRACE_FILE=$(ls -t "${LOGS_DIR}"/trace_*.perfetto-trace 2>/dev/null | head -1)
  if [ -z "$TRACE_FILE" ]; then
    echo "错误: 未找到 trace 文件。请指定 .perfetto-trace 文件路径。"
    echo "用法: $0 [trace文件路径]"
    exit 1
  fi
fi

if [ ! -f "$TRACE_FILE" ]; then
  echo "错误: 文件不存在: $TRACE_FILE"
  exit 1
fi

if ! command -v "${TP_SHELL}" &>/dev/null && [ ! -x "${TP_SHELL}" ]; then
  echo "错误: trace_processor_shell 未找到: ${TP_SHELL}"
  echo "请安装: https://perfetto.dev/docs/quickstart/trace-analysis"
  exit 1
fi

# --- SQL 查询函数 ---
query() {
  "${TP_SHELL}" -Q "$1" "${TRACE_FILE}" 2>/dev/null
}

# 通用聚合查询：按 slice name 汇总耗时
query_slices() {
  local pattern="$1"
  query "
SELECT
  s.name,
  COUNT(*) AS cnt,
  ROUND(MIN(s.dur)/1e6, 3) AS min_ms,
  ROUND(AVG(s.dur)/1e6, 3) AS avg_ms,
  ROUND(MAX(s.dur)/1e6, 3) AS max_ms,
  ROUND(SUM(s.dur)/1e6, 3) AS total_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
WHERE t.name LIKE '${APP_THREAD}%'
  AND s.name LIKE '${pattern}'
GROUP BY s.name
ORDER BY total_ms DESC;
"
}

echo "========================================"
echo "  YaYa Trace 分析"
echo "  文件: $(basename "${TRACE_FILE}")"
echo "========================================"

# ========== 1. 年月视图切换 ==========
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  1. 年月视图切换 (Transitions)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query_slices "MonthView→YearView"
echo ""
query_slices "YearView→MonthView"
echo ""
query_slices "YearView:SelectMonth"

# ========== 2. 主要 Compose 重组 ==========
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  2. 主要 Compose 重组 (Recomposition)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query_slices "MonthView:Compose"
echo ""
query_slices "YearView:Compose"
echo ""
query_slices "CalendarPagerArea"
echo ""

# ========== 3. 月视图分页 ==========
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  3. 月视图分页 (CalendarPager)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query "
SELECT
  s.name,
  COUNT(*) AS cnt,
  ROUND(MIN(s.dur)/1e6, 3) AS min_ms,
  ROUND(AVG(s.dur)/1e6, 3) AS avg_ms,
  ROUND(MAX(s.dur)/1e6, 3) AS max_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
WHERE t.name LIKE '${APP_THREAD}%'
  AND s.name LIKE 'CalendarPager:Page:%'
GROUP BY s.name
ORDER BY max_ms DESC
LIMIT 20;
"
echo ""

# ========== 4. 周视图分页 ==========
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  4. 周视图分页 (WeekPager)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query "
SELECT
  s.name,
  COUNT(*) AS cnt,
  ROUND(MIN(s.dur)/1e6, 3) AS min_ms,
  ROUND(AVG(s.dur)/1e6, 3) AS avg_ms,
  ROUND(MAX(s.dur)/1e6, 3) AS max_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
WHERE t.name LIKE '${APP_THREAD}%'
  AND s.name LIKE 'WeekPager:Page:%'
GROUP BY s.name
ORDER BY max_ms DESC
LIMIT 20;
"
echo ""

# ========== 5. 年视图网格 ==========
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  5. 年视图网格 (YearGridView)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query_slices "YearGridView:%"
echo ""
query_slices "generateMiniMonthDays:%"

# ========== 6. 月视图网格计算 ==========
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  6. 月视图网格计算 (getMonthDays)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query_slices "getMonthDays:%"
echo ""

# ========== 7. 动画状态 ==========
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  7. 动画状态 (VM:collapseProgress)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query "
SELECT
  COUNT(*) AS updates,
  ROUND(AVG(s.dur)/1e6, 3) AS avg_ms,
  ROUND(MAX(s.dur)/1e6, 3) AS max_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
WHERE t.name LIKE '${APP_THREAD}%'
  AND s.name = 'VM:collapseProgress';
"

# ========== 8. 超过 16.67ms 的慢帧重组 ==========
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  8. 超过 16.67ms 的慢重组 (>1 frame budget)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query "
SELECT
  s.name,
  ROUND(s.dur/1e6, 3) AS dur_ms,
  ROUND(s.ts/1e9, 3) AS timestamp_s
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
WHERE t.name LIKE '${APP_THREAD}%'
  AND s.dur > 16670000
  AND (s.name LIKE '%:Compose' OR s.name LIKE '%Page:%' OR s.name LIKE '%GridView:%')
ORDER BY s.dur DESC
LIMIT 20;
"

# ========== 9. 整体统计 ==========
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  9. 应用线程 Slice 总览"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
query "
SELECT
  COUNT(*) AS total_slices,
  ROUND(SUM(s.dur)/1e6, 3) AS total_dur_ms,
  ROUND(AVG(s.dur)/1e6, 3) AS avg_ms,
  ROUND(MAX(s.dur)/1e6, 3) AS max_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
WHERE t.name LIKE '${APP_THREAD}%';
"

echo ""
echo "========================================"
echo "  分析完成"
echo "========================================"
