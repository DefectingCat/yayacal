<!-- Parent: ../../../../../../../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-06-15 -->

# baseline

## Purpose
Baseline Profile / Startup Profile 自动生成器与启动性能基准测试。包含：
- `BaselineProfileGenerator.kt`：通过 UI Automator 模拟核心用户交互路径（冷启动、FAB 展开、显示调休切换、CalendarPager 翻页、日期选择、BottomCard 折叠/展开），同时生成 Baseline Profile（AOT）与 Startup Profile（DEX layout）两份产物。
- `StartupBenchmark.kt`：冷启动性能基准测试，测量 `timeToInitialDisplay` 与 `timeToFullDisplay`。

## Key Files

| File | Description |
|------|-------------|
| `BaselineProfileGenerator.kt` | Profile 生成测试类，覆盖启动与核心交互路径，生成 `*-baseline-prof.txt` 与 `*-startup-prof.txt` |
| `StartupBenchmark.kt` | 冷启动基准测试类，覆盖 Full/Partial/None 三种编译模式 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 新增交互路径需在 `BaselineProfileGenerator.kt` 的 `profileBlock` 中添加对应步骤
- 使用 `device.findObject(By.res(...))` 或 `device.findObject(By.text(...))` 定位 UI 元素
- 每个操作后调用 `device.waitForIdle()` 等待动画完成

### Testing Requirements
- 生成 Profile：`./gradlew :macrobenchmark:updateBaselineProfile`
  - Baseline Profile 输出：`core/src/main/baseline-prof.txt`
  - Startup Profile 输出：`core/src/main/baselineProfiles/startup-prof.txt`
- 运行冷启动基准（仅该类）：`./gradlew :macrobenchmark:connectedBenchmarkAndroidTest --tests "plus.rua.project.baseline.StartupBenchmark"`
- 需要设备/模拟器连接，应用已安装在 benchmark 构建类型下

## Dependencies

### External
- `androidx.benchmark.macro.junit4.BaselineProfileRule`
- `androidx.benchmark.macro.junit4.MacrobenchmarkRule`
- `androidx.test.uiautomator`

<!-- MANUAL: -->
