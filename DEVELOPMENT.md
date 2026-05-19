# 开发指南

## 环境要求

- JDK 17+
- Android Studio (Ladybug 或更新版本)
- Xcode 16+ (仅 iOS 构建需要)
- Kotlin Multiplatform 插件 (Android Studio 内置)

## 项目结构

```
YaYa/
├── shared/                          # 共享模块 — 所有 UI 和业务逻辑
│   ├── src/commonMain/kotlin/       # 跨平台代码
│   │   └── plus/rua/project/
│   │       ├── App.kt               # 应用入口
│   │       ├── CalendarViewModel.kt  # 日历状态管理
│   │       └── ui/
│   │           ├── CalendarMonthView.kt   # 顶层日历屏幕
│   │           ├── CalendarMonthPage.kt   # 单月网格页
│   │           ├── CalendarPager.kt       # 月视图无限分页
│   │           ├── WeekPager.kt           # 周视图无限分页
│   │           ├── DayCell.kt             # 单日圆圈组件
│   │           ├── MonthHeader.kt         # 年月标题 + 周数
│   │           ├── WeekdayHeader.kt       # 星期标题行
│   │           └── BottomCard.kt          # 底部拖拽卡片
│   ├── src/commonTest/kotlin/       # 共享测试
│   ├── src/androidMain/kotlin/      # Android 预览工具
│   └── src/iosMain/kotlin/          # iOS ViewController 工厂
├── androidApp/                      # Android 薄壳 — MainActivity → App()
├── iosApp/                          # iOS 入口 — Xcode 项目
└── gradle/libs.versions.toml        # 版本目录 — 统一管理依赖版本
```

## 运行

### Android

```bash
# 命令行构建
./gradlew :androidApp:assembleDebug

# 安装到设备
./gradlew :androidApp:installDebug
```

或在 Android Studio 中选择 `androidApp` 配置直接运行。

### iOS

1. 先执行一次 Gradle 同步：`./gradlew :shared:generateDummyFramework`
2. 在 Xcode 中打开 `iosApp/iosApp.xcworkspace`
3. 选择目标设备或模拟器，点击 Run

> 首次打开可能需要等待 Xcode 索引完成。如果报 framework 错误，重新执行 Gradle 同步即可。

## 测试

```bash
# 运行所有共享模块测试
./gradlew :shared:allTests

# 运行单个测试类 (Android host)
./gradlew :shared:androidHostTest --tests "plus.rua.project.ComposeAppCommonTest"
```

## 开发约定

### 代码组织

- 所有 Compose UI 和 ViewModel 代码放在 `shared/commonMain`，不按平台拆分
- 平台特定代码仅放在对应的 `androidMain` / `iosMain`
- UI 组件统一在 `plus.rua.project.ui` 包下

### Compose 规范

- `Modifier` 参数始终放在最后
- 回调参数使用 `on` 前缀：`onDateClick`、`onMonthChanged`
- 公开 `@Composable` 函数需要 KDoc 注释（详见 `COMMENTS.md`）

### 日期处理

- 统一使用 `kotlinx-datetime`，禁止使用 `java.util.Calendar`
- 周起始为周一 (ISO 8601)
- `monthNumber` 访问需要 `@Suppress("DEPRECATION")` 并附行内注释说明原因

### UI 文案

- 界面文字为中文（星期标题 "一二三四五六日"，月份格式 "2026年5月"）

### 依赖管理

- 所有版本声明在 `gradle/libs.versions.toml`，不硬编码
- 新增依赖先在版本目录添加条目，再在 `build.gradle.kts` 中引用

## 架构概览

```
CalendarMonthView (顶层屏幕)
  ├── MonthHeader          年月标签 + ISO 周数
  ├── WeekdayHeader        固定星期行
  ├── CalendarPager        月视图无限分页 (Int.MAX_VALUE 页)
  │     └── CalendarMonthPage  6×7 DayCell 网格，折叠时压缩非选中行
  │           └── DayCell      单日圆圈，选中/今日状态
  ├── WeekPager            周视图无限分页 (折叠态)
  │     └── DayCell
  └── BottomCard           拖拽手柄，驱动折叠/展开手势
```

**折叠动画：** `CalendarViewModel.collapseProgress` 控制 0f(月)↔1f(周) 过渡。`BottomCard` 捕获垂直拖拽，释放时超过 50% 则弹簧动画吸附到最近状态。完全折叠后 `WeekPager` 替代 `CalendarPager` 实现高效单周分页。

**分页映射：** 两个 Pager 均使用 `Int.MAX_VALUE` 页数，中心页为 `Int.MAX_VALUE / 2`。页码到日期为算术转换，无索引列表。两者均跳过初始 `snapshotFlow` 发射 (`.drop(1)`) 以保留首次渲染时的"今日"选中。

## 性能排查（Perfetto / Systrace）

项目使用 `composeTraceBeginSection` / `composeTraceEndSection` 在关键代码段插入 trace marker，Android 上会被记录到系统 trace 中。iOS 为空操作。

已有的 trace section：
- `MonthView:Compose` / `YearView:Compose` — 顶层重组耗时
- `YearView→MonthView` / `MonthView→YearView` — 年视图切换动画
- `YearGridView:$year` / `generateMiniMonthDays:$year-$month` — 年网格渲染
- `getMonthDays:$year-$month` — 月网格数据生成

### 分析折叠器卡顿的方法

1. **录制 trace**：Android Studio → Profiler → CPU → 选择 "Trace Java Methods" 或命令行：
   ```bash
   adb shell perfetto -c - --txt \<<EOF
   buffers: { size_kb: 65536 }
   data_sources: {
     config {
       name: "linux.ftrace"
       ftrace_config {
         ftrace_events: "ftrace/print"
         ftrace_events: "sched/sched_switch"
         buffer_size_kb: 8192
       }
     }
   }
   data_sources: {
     config {
       name: "android.packages_list"
     }
   }
   duration_ms: 10000
   EOF
   ```

2. **用 Python 分析 trace**（无需 Perfetto UI）：

   ```python
   def read_varint(data, offset):
       result = 0; shift = 0
       while offset < len(data):
           byte = data[offset]
           result |= (byte & 0x7F) << shift
           offset += 1
           if not (byte & 0x80): break
           shift += 7
       return result, offset

   def parse_trace(path):
       with open(path, 'rb') as f:
           data = f.read()

       # 1) 读取所有 TracePacket
       packets = []
       offset = 0
       while offset < len(data):
           if data[offset] != 0x0a:
               offset += 1; continue
           offset += 1
           try:
               length, new_offset = read_varint(data, offset)
               if 0 < length < 1_000_000 and new_offset + length <= len(data):
                   packets.append(data[new_offset:new_offset + length])
                   offset = new_offset + length
               else:
                   offset = new_offset
           except:
               offset += 1

       # 2) 在 ftrace_events 中搜索自定义 marker
       events = []
       for pkt in packets:
           # 找 field 2 (ftrace_events bundle)
           po = 0
           while po < len(pkt):
               if po >= len(pkt): break
               tag = pkt[po]; po += 1
               fn = tag >> 3; wt = tag & 0x07
               if wt == 0:
                   _, po = read_varint(pkt, po)
               elif wt == 2:
                   length, po = read_varint(pkt, po)
                   chunk = pkt[po:po + length]
                   if fn == 2:
                       # 扫描 bundle 内的 FtraceEvent (field 1, 0x0a)
                       eo = 0
                       while eo < len(chunk):
                           if chunk[eo] != 0x0a:
                               eo += 1; continue
                           eo += 1
                           try:
                               el, eno = read_varint(chunk, eo)
                               if el > 0 and eno + el <= len(chunk):
                                   evt = chunk[eno:eno + el]
                                   # 提取 timestamp (field 1, varint)
                                   if len(evt) > 1 and evt[0] == 0x08:
                                       ts, _ = read_varint(evt, 1)
                                       # 搜索 marker 字符串
                                       for pat in [b'BC:', b'VM:', b'MonthView:']:
                                           idx = evt.find(pat)
                                           if idx >= 0:
                                               me = idx
                                               while me < len(evt) and 32 <= evt[me] < 127:
                                                   me += 1
                                               name = evt[idx:me].decode()
                                               events.append((ts, name))
                                               break
                                   eo = eno + el
                               else:
                                   eo = eno
                           except:
                               eo += 1
                   po += length
               elif wt in (1, 5):
                   po += 8 if wt == 1 else 4
               else:
                   break

       events.sort()
       return events

   # 使用
   events = parse_trace('cpu-perfetto-xxxx.trace')
   for ts, name in events:
       print(f"{ts}: {name}")
   ```

3. **关注点**：
   - **触摸事件间隔**：统计相邻 `BC:delta` marker 的时间差。理想间隔 ≤16ms；若出现 >33ms 说明丢帧，>100ms 说明触摸断流。
   - **重组耗时**：`VM:collapseProgress` → `MonthView:Compose` 的间隔，应在亚毫秒级。
   - **ViewModel → Compose 延迟**：从 `snapTo` 调用到下一帧重组完成的间隔。

### 已知排查结论（2026-05-19）

对折叠器 trace 的分析显示：
- **重组本身很快**（VM progress → Compose 约 500μs），不是卡顿来源。
- **触摸事件采样间隔不均匀**是主要问题。某些拖拽序列中出现 30-50ms 的触摸事件间隔，偶尔有 >100ms 的断流。这属于系统/模拟器层的事件分发问题，而非 Compose 代码问题。
- 若在真机上复现，建议检查是否有 CPU 抢占或手指短暂离屏。
