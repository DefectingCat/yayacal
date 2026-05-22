<!-- Parent: ../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-05-22 -->

# macrobenchmark

## Purpose
宏基准测试模块，使用 Android Baseline Profile 技术自动生成启动优化配置文件。通过 UI Automator 模拟真实用户交互路径，收集 AOT 编译所需的 profile 数据。

## Key Files

| File | Description |
|------|-------------|
| `build.gradle.kts` | 模块构建配置（`com.android.test` 插件、benchmark 构建类型、Baseline Profile 自动复制任务） |

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `src/main/java/plus/rua/project/baseline/` | Baseline Profile 生成器（见 `src/main/java/plus/rua/project/baseline/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 修改此模块需连接 Android 设备/模拟器运行
- Baseline Profile 一键生成：`./gradlew :macrobenchmark:updateBaselineProfile`
- 仅运行基准测试：`./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`
- 生成的 profile 自动复制到 `core/src/main/baseline-prof.txt`

### Testing Requirements
- 需要已安装应用的 benchmark 构建类型
- 模拟器需启用 GPU 加速（software renderer 不支持 gfxinfo framestats）

## Dependencies

### Internal
- `:app` 模块 — 目标测试应用
- `:core` 模块 — profile 自动复制到 `core/src/main/baseline-prof.txt`

### External
- `androidx.benchmark:benchmark-macro-junit4`
- `androidx.test.uiautomator`
- `androidx.test.ext:junit`

<!-- MANUAL: -->
