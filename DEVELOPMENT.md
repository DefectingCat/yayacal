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
