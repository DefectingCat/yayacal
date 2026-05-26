#requires -Version 5.1

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [int]$Duration = 8,

    [switch]$NoLaunch,
    [switch]$Trace,
    [switch]$Help
)

if ($Help) {
    $helpLines = @(
        "用法: .\scripts\profile.ps1 [秒数] [-NoLaunch] [-Trace]"
        ""
        "选项:"
        "  秒数       抓取时长（默认 8 秒）"
        "  -NoLaunch  不自动启动应用"
        "  -Trace     使用 trace 构建（release 优化 + 保留 trace 标记）"
        "  -Help      显示此帮助"
    )
    $helpLines | ForEach-Object { Write-Host $_ }
    exit 0
}

$PACKAGE      = "plus.rua.project"
$ACTIVITY     = "plus.rua.project.MainActivity"
$PROJECT_ROOT = Split-Path -Parent $PSScriptRoot
$LOGS_DIR     = Join-Path $PROJECT_ROOT "logs"

$DURATION_MS  = $Duration * 1000
$TIMESTAMP    = Get-Date -Format "yyyyMMdd_HHmmss"

Write-Host "========================================"
Write-Host "  YaYa 性能追踪"
Write-Host "  包名: $PACKAGE"
Write-Host "  时长: ${Duration}s"
if ($Trace) {
    Write-Host "  构建: trace (release + trace)"
} else {
    Write-Host "  构建: debug"
}
Write-Host "  输出: $LOGS_DIR/"
Write-Host "========================================"

New-Item -ItemType Directory -Force -Path $LOGS_DIR | Out-Null

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Error "adb 未找到。请确保 Android SDK 的 platform-tools 在 PATH 中。"
    exit 1
}

$deviceLines = & adb devices | Select-String "device$"
$deviceCount = ($deviceLines | Measure-Object).Count
if ($deviceCount -eq 0) {
    Write-Error "没有已连接的 Android 设备。"
    exit 1
}
if ($deviceCount -gt 1) {
    Write-Warning "检测到多个设备，将使用默认设备。"
}

$installed = & adb shell pm list packages | Select-String $PACKAGE
if (-not $installed) {
    Write-Error "应用 $PACKAGE 未安装。请先运行 .\gradlew :app:installDebug"
    if ($Trace) {
        Write-Host "       或使用 .\gradlew :app:installTrace 安装 trace 构建"
    }
    exit 1
}

if (-not $NoLaunch) {
    Write-Host ""
    Write-Host "[1/5] 启动应用..."
    & adb shell am start -n "$PACKAGE/$ACTIVITY" 2>$null
    Start-Sleep -Seconds 2
} else {
    Write-Host ""
    Write-Host "[1/5] 跳过启动 (-NoLaunch)"
}

Write-Host ""
Write-Host "[2/5] 抓取 Perfetto trace (${Duration}s)..."
Write-Host "      请在设备上操作应用，trace 正在记录..."

$TRACE_FILE    = "/data/misc/perfetto-traces/yaya_${TIMESTAMP}.perfetto-trace"
$LOCAL_TRACE   = Join-Path $LOGS_DIR "trace_${TIMESTAMP}.perfetto-trace"
$LOCAL_CONFIG  = Join-Path $LOGS_DIR ".perfetto_config_${TIMESTAMP}.txt"
$DEVICE_CONFIG = "/data/misc/perfetto-configs/yaya_config_${TIMESTAMP}.txt"

$perfettoLines = @(
    "buffers {"
    "  size_kb: 131072"
    "  fill_policy: RING_BUFFER"
    "}"
    "buffers {"
    "  size_kb: 4096"
    "  fill_policy: RING_BUFFER"
    "}"
    "data_sources {"
    "  config {"
    "    name: `"linux.ftrace`""
    "    ftrace_config {"
    "      ftrace_events: `"sched/sched_switch`""
    "      ftrace_events: `"sched/sched_wakeup`""
    "      ftrace_events: `"power/cpu_frequency`""
    "      ftrace_events: `"power/cpu_idle`""
    "      atrace_categories: `"gfx`""
    "      atrace_categories: `"view`""
    "      atrace_categories: `"wm`""
    "      atrace_categories: `"am`""
    "      atrace_categories: `"input`""
    "      atrace_categories: `"sched`""
    "      atrace_categories: `"freq`""
    "      atrace_categories: `"idle`""
    "      atrace_apps: `"$PACKAGE`""
    "    }"
    "  }"
    "}"
    "data_sources {"
    "  config {"
    "    name: `"android.gpu.memory`""
    "  }"
    "}"
    "data_sources {"
    "  config {"
    "    name: `"android.surfaceflinger.frametimeline`""
    "  }"
    "}"
    "data_sources {"
    "  config {"
    "    name: `"linux.process_stats`""
    "    target_buffer: 1"
    "    process_stats_config {"
    "      scan_all_processes_on_start: true"
    "    }"
    "  }"
    "}"
    "duration_ms: $DURATION_MS"
)
$perfettoConfig = $perfettoLines -join "`r`n"

$perfettoConfig | Set-Content -Encoding UTF8 -Path $LOCAL_CONFIG

& adb push "$LOCAL_CONFIG" "$DEVICE_CONFIG" | Out-Null
Remove-Item -Force -Path $LOCAL_CONFIG -ErrorAction SilentlyContinue

& adb shell "perfetto --txt -c $DEVICE_CONFIG -o $TRACE_FILE"
& adb shell "rm -f $DEVICE_CONFIG"

Write-Host "      拉取 trace 文件..."
& adb pull "$TRACE_FILE" "$LOCAL_TRACE"
& adb shell "rm -f $TRACE_FILE" 2>$null

Write-Host ""
Write-Host "[3/5] 抓取帧统计..."
$FRAMESTATS_FILE = Join-Path $LOGS_DIR "framestats_${TIMESTAMP}.txt"
& adb shell dumpsys gfxinfo "$PACKAGE" framestats | Set-Content -Encoding UTF8 -Path $FRAMESTATS_FILE

Write-Host ""
Write-Host "[4/5] 抓取内存信息..."
$MEMINFO_FILE = Join-Path $LOGS_DIR "meminfo_${TIMESTAMP}.txt"
& adb shell dumpsys meminfo "$PACKAGE" | Set-Content -Encoding UTF8 -Path $MEMINFO_FILE

Write-Host ""
Write-Host "[5/5] 生成摘要..."
$REPORT_FILE = Join-Path $LOGS_DIR "report_${TIMESTAMP}.md"

$frameStatsContent = Get-Content -Path $FRAMESTATS_FILE -Raw
$FRAME_COUNT = ([regex]::Matches($frameStatsContent, "FrameTimeline")).Count

function Get-FirstMatchValue {
    param([string]$Pattern, [string]$Content, [int]$CaptureGroup = 1)
    $m = [regex]::Match($Content, $Pattern)
    if ($m.Success) { $m.Groups[$CaptureGroup].Value.Trim() } else { "" }
}

$TOTAL_FRAMES = Get-FirstMatchValue "Total frames rendered:\s*(\d+)"    $frameStatsContent
$JANKY_FRAMES = Get-FirstMatchValue "Janky frames:\s*(\d+)"             $frameStatsContent
$JANKY_PERCENT = Get-FirstMatchValue "Janky frames:.*?\(([^)]+)\)"      $frameStatsContent
$P50           = Get-FirstMatchValue "50th percentile:\s*([\d.]+)"       $frameStatsContent
$P90           = Get-FirstMatchValue "90th percentile:\s*([\d.]+)"       $frameStatsContent
$P99           = Get-FirstMatchValue "99th percentile:\s*([\d.]+)"       $frameStatsContent
$SLOW_UI       = Get-FirstMatchValue "Number Slow UI thread:\s*(\d+)"   $frameStatsContent
$SLOW_DRAW     = Get-FirstMatchValue "Number Slow issue draw commands:\s*(\d+)" $frameStatsContent
$HIGH_INPUT    = Get-FirstMatchValue "Number High input latency:\s*(\d+)"        $frameStatsContent

$APP_VERSION = (& adb shell dumpsys package "$PACKAGE" | Select-String "versionName" | Select-Object -First 1) -replace ".*versionName=", "" -replace "\s.*", "" -replace "`r", ""
if (-not $APP_VERSION) { $APP_VERSION = "unknown" }

$DEVICE_MODEL = (& adb shell getprop ro.product.model 2>$null).Trim()
$ANDROID_VERSION = (& adb shell getprop ro.build.version.release 2>$null).Trim()

$memInfoContent = Get-Content -Path $MEMINFO_FILE -Raw
$TOTAL_PSS  = Get-FirstMatchValue "TOTAL PSS:\s*(\d+)"  $memInfoContent
$JAVA_HEAP  = Get-FirstMatchValue "^\s*Java Heap:\s*(\d+)" $memInfoContent
$NATIVE_HEAP = Get-FirstMatchValue "^\s*Native Heap:\s*(\d+)" $memInfoContent
$GRAPHICS   = Get-FirstMatchValue "^\s*Graphics:\s*(\d+)" $memInfoContent

$buildType = if ($Trace) { "trace" } else { "debug" }
$now = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

$reportLines = @(
    "# YaYa 性能追踪报告"
    ""
    "**时间:** $now"
    "**设备:** $DEVICE_MODEL (Android $ANDROID_VERSION)"
    "**应用版本:** $APP_VERSION"
    "**构建类型:** $buildType"
    "**追踪时长:** ${Duration}s"
    ""
    "## 文件清单"
    ""
    "| 文件 | 说明 |"
    "|------|-------------|"
    "| ``trace_${TIMESTAMP}.perfetto-trace`` | Perfetto trace（在 https://ui.perfetto.dev 中打开） |"
    "| ``framestats_${TIMESTAMP}.txt`` | GPU 帧统计 |"
    "| ``meminfo_${TIMESTAMP}.txt`` | 内存快照 |"
    "| ``report_${TIMESTAMP}.md`` | 本报告 |"
    ""
    "## 帧率摘要"
    ""
    "| 指标 | 数值 |"
    "|------|------|"
    "| 总渲染帧数 | $TOTAL_FRAMES |"
    "| 掉帧数 | $JANKY_FRAMES |"
    "| 掉帧比例 | $JANKY_PERCENT |"
    "| 50th percentile | $P50 |"
    "| 90th percentile | $P90 |"
    "| 99th percentile | $P99 |"
    "| Slow UI thread | $SLOW_UI |"
    "| Slow draw commands | $SLOW_DRAW |"
    "| High input latency | $HIGH_INPUT |"
    ""
    "## 内存摘要"
    ""
    "| 指标 | 数值 (KB) |"
    "|------|----------|"
    "| Total PSS | $TOTAL_PSS |"
    "| Java Heap | $JAVA_HEAP |"
    "| Native Heap | $NATIVE_HEAP |"
    "| Graphics | $GRAPHICS |"
    ""
    "## Perfetto 分析指南"
    ""
    "打开 [Perfetto UI](https://ui.perfetto.dev)，上传 trace 文件："
    ""
    "### 1. 查看 Compose 自定义标记"
    "搜索以下 trace section："
    ""
    "**月视图相关：**"
    "- ``MonthView:Compose`` — 月视图整体重组"
    "- ``CalendarPagerArea`` — 日历分页器区域重组"
    "- ``CalendarPager:Page`` — 月视图单页重组"
    "- ``WeekPager:Page`` — 周视图单页重组"
    "- ``getMonthDays:*`` — 月份网格计算"
    ""
    "**年视图相关：**"
    "- ``YearView:Compose`` — 年视图整体重组"
    "- ``YearGridView:*`` — 年视图网格重组"
    "- ``generateMiniMonthDays:*`` — 迷你月日期计算"
    "- ``YearView:SelectMonth`` — 年视图选择月份"
    ""
    "**转场动画：**"
    "- ``MonthView->YearView`` — 月->年视图切换"
    "- ``YearView->MonthView`` — 年->月视图切换"
    "- ``VM:collapseProgress`` — 折叠动画状态更新"
    ""
    "**单日单元格：**"
    "- ``DayCell`` — 单个日期单元格（通过 transition label）"
    ""
    "### 2. 分析帧率"
    "在 ``framestats_${TIMESTAMP}.txt`` 中查看："
    "- ``FrameTimeline`` 行 — 每帧的 CPU/GPU 耗时"
    "- ``jank`` 标记 — 掉帧情况"
    ""
    "### 3. 内存分析"
    "在 ``meminfo_${TIMESTAMP}.txt`` 中关注："
    "- ``TOTAL`` 行 — 应用总内存占用"
    "- ``Graphics`` 行 — GPU 内存使用"
    "- ``Native Heap`` 行 — 原生堆内存"
    ""
    "### 4. 年月视图切换专项分析"
    ""
    "在 Perfetto 中按以下步骤分析转场性能："
    ""
    "1. 找到 ``MonthView->YearView`` 或 ``YearView->MonthView`` 标记"
    "2. 查看标记前后 500ms 的帧数据："
    "   - 查找超过 16.67ms 的帧（Choreographer#doFrame）"
    "   - 检查是否有连续多帧超过预算"
    "3. 同时搜索 ``MonthView:Compose`` 和 ``YearView:Compose``，观察重组重叠情况"
    "4. 查看 ``YearGridView:*`` 的耗时，年视图 12 个月网格的计算和绘制成本"
    ""
    "## 基线对比方法"
    ""
    "要对比优化前后的性能："
    ""
    "``````powershell"
    "# 1. 记录当前数据作为基线"
    ".\scripts\profile.ps1 -Trace 15"
    ""
    "# 2. 修改代码后重新编译"
    ".\gradlew :app:installTrace"
    ""
    "# 3. 再次记录"
    ".\scripts\profile.ps1 -Trace 15"
    ""
    "# 4. 对比两个 report 中的帧率摘要表格"
    "``````"
)
$report = $reportLines -join "`r`n"

$report | Set-Content -Encoding UTF8 -Path $REPORT_FILE

Write-Host ""
Write-Host "========================================"
Write-Host "  完成！"
Write-Host "========================================"
Write-Host ""
Write-Host "输出文件:"
Write-Host "  trace:      $LOCAL_TRACE"
Write-Host "  framestats: $FRAMESTATS_FILE"
Write-Host "  meminfo:    $MEMINFO_FILE"
Write-Host "  report:     $REPORT_FILE"
Write-Host ""
Write-Host "下一步:"
Write-Host "  1. 打开 https://ui.perfetto.dev"
Write-Host "  2. 上传 trace_${TIMESTAMP}.perfetto-trace"
Write-Host "  3. 搜索 'MonthView->YearView' 查看转场 trace"
Write-Host ""
