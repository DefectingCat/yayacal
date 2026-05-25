# 开发指南

## 性能追踪

使用 `scripts/profile.sh` 一键抓取 Perfetto trace、帧统计和内存快照。

```bash
# 默认抓取 8 秒
./scripts/profile.sh

# 抓取 15 秒
./scripts/profile.sh 15

# 应用已在运行时，不自动启动
./scripts/profile.sh --no-launch
```

输出文件保存在 `logs/` 目录：

| 文件 | 说明 |
|------|------|
| `trace_*.perfetto-trace` | Perfetto trace，在 https://ui.perfetto.dev 打开 |
| `framestats_*.txt` | GPU 帧统计 |
| `meminfo_*.txt` | 内存快照 |
| `report_*.md` | 追踪报告摘要 |

trace 中包含自定义标记：
- `MonthView:Compose` — 月视图重组
- `YearView:Compose` — 年视图重组
- `YearGridView:*` — 年视图网格组合（首帧耗时关键指标）
- `generateMiniMonthDays:*` — 月份网格计算
- `VM:collapseProgress` — 折叠动画

### 已知性能瓶颈

#### AnimatedGif 持续解码

`AnimatedGif` 中的 250×250 WebP 动画在 `repeatCount` 未限制时会以 11-14 FPS 无限循环解码，
持续消耗 CPU/GPU。已通过 `ImageOptions { repeatCount(2) }` 限制播放 3 次后停止。

#### YearGridView 首帧 168ms

切换到年视图时，`YearGridView` 首次组合需要：
- 创建 12 个 MiniMonth composable（含 `sharedElement` + `clickable` + `Canvas` 等 modifier 节点）
- 124 次文本测量（93 日期 + 24 标题 + 7 星期）
- `SharedTransitionLayout` 注册 12 个共享元素

文本测量已通过 `produceState` 延迟到第二帧执行，首帧 Canvas 渲染为空。
剩余 ~140ms 是 12 个 composable 节点创建 + layout 的 Compose 运行时开销，
需要通过 **Baseline Profile** 预编译相关类来优化。

确保 `macrobenchmark` 的 `BaselineProfileGenerator` 覆盖以下路径：
- 冷启动 → FAB → 年视图（触发 YearGridView 首次组合）
- 年视图 → 点击任意月份返回月视图（触发 sharedElement 转场）

#### CalendarPager 预加载

`beyondViewportPageCount` 已设为 0，避免翻页时预加载相邻月页导致的帧丢失。
如需恢复预加载，注意 `compose:lazy:prefetch` 可能产生 400-700ms 卡顿。

## Baseline Profile

```bash
# 编译 Android debug APK
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug

# 编译 release APK（含 Baseline Profiles）
./gradlew :app:assembleRelease

./gradlew :app:installBenchmark
```

Baseline Profile 自动生成器。

运行方式（一键生成 + 自动复制到 :core）：

```
./gradlew :macrobenchmark:updateBaselineProfile
```

仅运行基准测试（不自动复制）：

```
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

手动复制路径：
`macrobenchmark/build/outputs/connected_android_test_additional_output/`

测试覆盖全部用户交互路径，实现全量 AOT：

1. 冷启动 → 首帧渲染
2. FAB 展开 → 年视图 → 月视图
3. 日期选择 → 周视图折叠/展开
4. 关于页 → 开源许可页
5. 返回主界面

## 模拟器

```
emulator -avd Pixel_10 \
  -no-snapshot \
  -no-boot-anim \
  -gpu host \
  -accel on \
  -cores 4 \
  -memory 4096 \
  -partition-size 2048
```
