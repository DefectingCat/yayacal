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
