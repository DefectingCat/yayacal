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

| 文件                     | 说明                                            |
| ------------------------ | ----------------------------------------------- |
| `trace_*.perfetto-trace` | Perfetto trace，在 https://ui.perfetto.dev 打开 |
| `framestats_*.txt`       | GPU 帧统计                                      |
| `meminfo_*.txt`          | 内存快照                                        |
| `report_*.md`            | 追踪报告摘要                                    |

trace 中包含自定义标记：

- `MonthView:Compose` — 月视图重组
- `CalendarPagerArea` — 日历分页器区域
- `CalendarPager:Page:*` — 月视图单页重组
- `CalendarMonthPage:*` — 月页面数据计算（含折叠动画准备）
- `WeekPager:Page` — 周视图单页重组
- `YearView:Compose` — 年视图重组
- `YearGridView:*` — 年视图网格组合（首帧耗时关键指标）
- `generateMiniMonthDays:*` — 月份网格计算
- `MonthView→YearView` / `YearView→MonthView` — 视图切换
- `YearView:SelectMonth` — 年视图选月
- `getMonthDays:*` — ViewModel 月份网格计算
- `VM:collapseProgress:*` — 折叠动画拖拽（onDrag/onDragEnd/onExpandDrag/onExpandDragEnd）

## Baseline Profile / Startup Profile

```bash
# 编译 Android debug APK
./gradlew :app:assembleDebug

# 安装到设备
./gradlew :app:installDebug

# 编译 release APK（含 Baseline / Startup Profiles）
./gradlew :app:assembleRelease

# 安装 benchmark 构建类型 APK
./gradlew :app:installBenchmark
```

一键生成并复制到 `:core`：

```bash
./gradlew :macrobenchmark:updateBaselineProfile
```

生成后会得到两份产物：

| 产物 | 目标路径 | 用途 |
|------|--------|------|
| Baseline Profile | `core/src/main/baseline-prof.txt` | 指导 ART 做 AOT 编译 |
| Startup Profile | `core/src/main/baselineProfiles/startup-prof.txt` | 指导 AGP 做 DEX layout 优化 |

仅运行基准测试（不自动复制）：

```bash
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest
```

手动复制路径：
`macrobenchmark/build/outputs/connected_android_test_additional_output/`

注意：当前 `benchmark` 构建类型继承自 `release`（`isMinifyEnabled = true`），因此生成的文本 profile 会包含 R8 混淆后的类/方法名。AGP 在打包 APK/AAB 时会根据最终混淆映射将其转换为二进制 profile，这是正常行为。

## 模拟器

```sh
emulator -avd Pixel_10 \
  -no-snapshot \
  -no-boot-anim \
  -gpu host \
  -accel on \
  -cores 4 \
  -memory 4096 \
  -partition-size 2048
```

启动带 adb logcat 的模拟器

```sh
./gradlew :app:installDebug && rm -rf logs/1.logcat && adb logcat | tee logs/1.logcat
```
