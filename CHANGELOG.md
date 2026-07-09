# Changelog

All notable changes to the YaYa project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.3.0] - 2026-07-09

### Added

- 班次设置页：新增独立设置页，支持编辑基础周期（锚点日期 + 预设方案 1班1休/2班2休/3班3休/4班4休）。
- 班次设置页：迷你月历支持点击翻转班/休（单日）、长按翻转并从次日起重排后续周期。
- 班次设置页：迷你月历支持左右滑动翻月，点击年月标题弹 DatePicker 快速跳转月份。
- 班次设置页：滑动翻月时高度插值平滑过渡，复用主日历的行数插值机制。
- 班次设置页：恢复默认改为 Snackbar 撤销模式，5 秒内可恢复。

### Changed

- 班次数据模型重构：`phaseBreaks` + `override` 两独立字段合并为原子结构 `RephaseFlip(date, flippedTo, rephaseFrom)`，解决撤销误删、幽灵重排、断点链不级联问题。
- 个人轮班设置持久化到 SharedPreferences，设置返回主界面后 `onResume` 立即生效。
- 迷你月历角标颜色按类别配对（班=onPrimary，休=onError，重排起点=onTertiary）。
- 迷你月历动态计算行数（4/5/6 行自适应），不再固定 6 行。
- 依赖升级：Compose BOM 2026.06.01、Lifecycle 2.11.0、UiAutomator 2.4.0、tyme4kt 1.5.0、Spotless 8.8.0。

### Fixed

- 班次设置页：修复 `shiftPattern` StateFlow 未被 `collectAsState` 订阅导致设置返回后日历不立即刷新。
- 班次设置页：修复锚点日期 TextButton 内边距导致与周期行右侧不对齐。
- 班次设置页：修复顶部内边距比四周偏大的叠加问题。

## [1.2.0] - 2026-06-18

### Added

- 启动页：新增自定义 `SplashActivity` 与启动主题，集成 `core-splashscreen` 与 `reportFullyDrawn`。
- 月视图：年月标题支持日期选择器快速跳转。
- 关于页：连续点击版本号 7 次触发「小狗乐园」全屏视频彩蛋。
- 日期图标：新增玫瑰节（Rose Day）与生日皇冠动画。
- 日期检查器：添加数据持久化、空状态提示、恢复默认按钮，以及生产日期/保质期非法日期禁选。
- 图标：新增 API 26+ Adaptive Icon。
- Baseline Profile：拆分并同时集成 Baseline Profile 与 Startup Profile 到 `:core`。
- Macrobenchmark：新增 StartupBenchmark 冷启动耗时测试。

### Changed

- 应用图标：调整小黄鸭占比，优化 Adaptive Icon 前景尺寸。
- 资源整理：GIF 目录重命名为 `animations`，`AnimatedGif` 改为 `AnimatedWebp`，WebP 文件列表由 BuildConfig 注入以消除硬编码。
- 构建清理：移除无效 ProGuard 规则、调试日志，trace 标记解耦 ViewModel。
- UI 统一：日期检查器、FAB 菜单、返回图标等手绘 Canvas 图标替换为 Material Icons。
- 关于页背景图改为 WebP 格式。
- 版本号统一由 `gradle.properties` 管理。

### Fixed

- 启动页：修复 SplashScreen API 在某些设备上的崩溃/黑屏问题。
- 图标：修复 Adaptive Icon 前景加载失败及含背景问题。
- 日期检查器：修复历史负保质期数据导致列表异常，修复右滑删除卡住及 FAB 遮挡底部问题。

## [1.1.0] - 2026-06-02

### Added

#### Date Checker Tool
- New "Date Checker" tool page accessible from FAB → Tools menu for tracking item expiration dates
- Swipe-to-delete with animated removal and staggered enter/exit animations
- Expired status display with visual indicators
- Auto-scroll and highlight animation for new entries

#### Tools Page
- New "Tools" entry in FAB menu linking to a dedicated tools landing page
- Date Checker as the first tool module

#### Theme & Visual
- `YaYaTheme` introduced and applied to all Activities for unified theming
- Legal holiday badges now display with colored background and continuous edge rounded corners
- Holiday badge wave-scale entrance animation
- Personal shift badges redesigned with light circle background + centered text
- Shift badge circular base to avoid overlapping with selection ring

#### Year ↔ Month View Transition
- BottomCard slide-in animation and fade effect during year/month view transitions
- Month→year view no longer forces collapse state to expand

#### Performance
- LunarCache LRU cache for lunar/solar term calculations with startup pre-computation
- Macrobenchmark module with automated Baseline Profile generation
- Baseline Profile covering date checker, shift settings, tools page, and core calendar scenarios
- `ComposeTrace` cross-platform trace markers for Perfetto/Systrace
- SolarDay static cache to eliminate repeated object creation
- MiniMonth pure Canvas rendering eliminating 96 Text measurement overhead
- `graphicsLayer(translationY)` replacing `offset(Dp)` to avoid layout passes
- Aggregated `CalendarUiState` to reduce Compose recomposition
- `remember` stabilization for lambdas and computations
- Scene-based `profile.sh` with `--all` batch mode for 15 automated trace scenarios
- Perfetto trace analysis script (`analyze-trace.sh`)
- Trace build type for release + retained trace markers

#### Build & Tooling
- Spotless 8.5.1 code formatter with ktlint integration
- `.editorconfig` for ktlint Composable function naming rules
- Dependency update checker and auto-upgrade tool integration
- `app_icon` shrunk to 512×512 and converted to WebP (446KB savings)
- 152 GIF assets batch-converted to animated WebP format
- `uiTooling` moved to `debugImplementation`; unused `@Preview` and `kotlin-test` entries removed
- `sketch` library for GIF/WebP display (`sketch-compose` + `sketch-animated-webp`)
- PowerShell performance tracing script (`profile.ps1`)

#### Documentation
- Comprehensive `AGENTS.md` at every directory level (root, app, core, scripts, etc.)
- Updated `DEVELOPMENT.md` with Perfetto trace analysis and emulator launch commands
- Updated `CLAUDE.md` to reflect pure Android project structure

### Changed

- Project migrated from Kotlin Multiplatform (KMP/CMP) to pure Android (`:app` + `:core`)
- All Compose UI and business logic consolidated into `:core` module; `:app` remains a thin shell
- Removed KMP/CMP plugins, iOS app module, and `:shared` module
- `androidApp` module renamed to `app`
- Collapse animation refactored: removed fling velocity threshold, now spring-driven
- `CalendarPager` ↔ `WeekPager` switching uses `AnimatedContent` for smooth crossfade
- Year view page year calculation uses `settledPage` to prevent flicker during swipe
- ViewModel decoupled from Compose runtime, migrated to `StateFlow`
- `LunarCache` made injectable with extracted repeated computations
- MenuItem and ToolItem unified to use `Card(onClick)` pattern
- Holiday badge null checks simplified to Elvis operator
- `@Suppress` annotations cleaned up with deprecated API replacements
- Removed unnecessary P0 code (custom combine, dead StateFlow, duplicate grid algorithms, runBlocking)
- Removed debug logging from LicensesScreen and BottomCard

### Fixed

- Lunar first-day month name no longer appends redundant "月" suffix
- Year view stale year display on enter
- Year view page year flicker during swipe transitions
- Collapse animation flicker when switching between CalendarPager and WeekPager
- Folded state cross-month dates not grayed out in week view
- Date checker swipe-to-delete state misalignment and deprecation warning
- Shared element transition animation loss after year view page change
- Night mode theme transparency issues with explicit background colors
- Predictive back gesture failure and end-of-animation flash on certain devices
- Back animation residual transition eliminated with `snapTo`
- Fast swipe collapse/expand failure, now uses progress threshold detection
- `graphicsLayer` optimization reverted due to excessive GPU compositing overhead on real devices
- Reverted shared element transitions in favor of zoom + fade animation

### Removed

- iOS app module (`iosApp/`) and all related Xcode project files
- `:shared` module and `shared/build.gradle.kts`
- Shared element transition animations (replaced by zoom + fade)
- Year/month scroll wheel picker with haptic feedback (reverted)
- Aliyun Maven mirrors (switched to Maven Central / Google)
- Unused Compose runtime ProGuard keep rules
- Temporary performance monitoring logs (trace markers retained)

## [1.0.0] - 2026-05-20

### Added

#### Project Foundation
- Kotlin Multiplatform project setup targeting Android and iOS with Compose Multiplatform
- Two-module architecture: `:shared` (UI + business logic) and `:androidApp` (thin Android shell)
- AGP 9.2.1, Gradle 9.5.1, Kotlin 2.3.21, JVM target 17
- Compile SDK / target SDK 37, minimum SDK 24
- Version catalog at `gradle/libs.versions.toml`
- Dynamic version name generation: `baseVersion + git hash + buildDate`

#### Calendar Core Views
- **Month View**: 6x7 day grid with dynamic row count (4/5/6 weeks) based on actual month shape
- **Week View**: Single-week horizontal pager activated by collapsing the month view
- **Year View**: 4x3 mini-month grid with swipe-based year navigation and Hero Zoom transition
- **Infinite Paging**: `Int.MAX_VALUE` virtual pages centered at `Int.MAX_VALUE / 2`, enabling boundless month/week/year navigation
- ISO 8601 week numbering with Monday as the first day of the week
- Today indicator with border outline; selected-today state uses `primaryContainer` for softer visual treatment
- Non-current-month days are grayed out for visual clarity
- Auto-select today (if within range) or first day / Monday on page changes
- Click month header to jump back to today
- Cross-month week selection: intelligently selects the appropriate date based on swipe direction

#### Collapse / Expand Animation
- Drag-driven month-to-week collapse gesture on the calendar grid
- Spring-based snap animation that auto-settles to the nearest state on release
- Two-phase whole-block slide-up collapse animation with fade-out for non-selected rows
- Dynamic drag range computed from actual visual height change for 1:1 finger tracking
- Fling velocity threshold (800 dp/s) for quick swipe snap
- Collapse threshold set to 8% for easy fold/unfold triggering
- Pull-down gesture to expand from collapsed week view back to month view
- Week pager cross-fade transition to eliminate blank gaps during page switches

#### Chinese Calendar (Lunar)
- Lunar date display below each day number (using `tyme4kt`), showing month name on the first day of each lunar month
- Twenty-four solar terms (节气) annotations
- Traditional lunar festivals (春节, 端午, 中秋, etc.)
- Western solar festivals
- Legal holiday and compensatory workday badges (休/班) in the top-right corner
- Priority-based annotation display: legal holidays > lunar festivals > solar terms > solar festivals > lunar day

#### Personal Shift Scheduling
- `ShiftPattern` data model with anchor date + cyclic sequence for periodic work/rest schedules
- Independent from public holidays
- Work/Rest capsule badge display on DayCell
- Configurable legal holiday overlay toggle (default off)
- Shift status tips in the bottom card

#### Visual Design & Animations
- Material 3 dynamic color scheme
- System dark theme support with automatic light/dark `ColorScheme` switching
- DayCell circular reveal animation with `updateTransition` for smooth state transitions
- Month header slide + fade transition when month/week number changes
- Page fade-in/fade-out transitions on CalendarPager and WeekPager
- GIF elastic entrance animation on switch
- 152 GIF assets displayed randomly based on selected date seed
- Custom app icon resources for all densities and platforms (PNG + WebP)

#### Bottom Card
- Draggable bottom info card with drag handle
- Selected date relative day description (今天, 昨天, 明天, N天前/后)
- Gregorian and lunar date details
- Shift status tips (WORK / OFF)
- Random GIF display (140dp height)

#### Navigation & Pages
- About screen with app icon, name, dynamic version, and open-source license entry
- Licenses screen listing all third-party dependency licenses
- Floating Action Button (FAB) with zoom-animated menu and scrim close
- Page navigation with direction-aware slide + fade transitions
- Android 13+ Predictive Back gesture with follow-finger displacement/scale animation
- BackHandler expect/actual for cross-platform back gesture interception

#### Performance Optimizations
- `ComposeTrace` cross-platform trace markers for Perfetto / Systrace analysis
- `graphicsLayer(translationY)` replacing `offset(Dp)` to avoid per-frame layout passes
- SolarDay static cache to eliminate repeated object creation during pager switches
- MiniMonth pure Canvas rendering: eliminated 96 Text component measurement overhead
- Year view / month view coexistence in composition tree with `Modifier.alpha` control (avoiding whole-tree destruction)
- Precomputed dp-to-px conversions and TextLayoutResult caches
- Pager cache optimized (`beyondViewportPageCount = 0`)

#### Build & Testing
- R8 code shrinking and resource optimization with ProGuard rules
- ABI filtering (`arm64-v8a`, `armeabi-v7a`)
- App Bundle language/density/ABI splits
- Unit tests for `CalendarViewModel`, `CalendarUtils`, and `ShiftPattern`
- `kotlinx-coroutines-test` for coroutine-based ViewModel testing
- ComposeTrace host test fallback (silently ignores when Trace API is not stubbed)

#### Documentation
- `CLAUDE.md` with project architecture, conventions, and build commands
- `DEVELOPMENT.md` with setup, build, test, and Perfetto trace analysis guide
- `COMMENTS.md` with commenting and KDoc conventions
- `README.md` with feature overview and tech stack

### Changed

- Row padding increased from 4dp to 6dp for better touch targets
- Selected-state animation duration reduced from 250ms to 150ms for snappier feedback
- Weekday header moved out of pager to remain fixed across page swipes
- Card gap spacing animates with collapse progress (24dp expanded → 12dp collapsed)
- Year view title bar displays lunar干支+生肖 year (e.g., 「丙午马年」) with a "今年" button for quick return
- FAB fixed to bottom-left of screen instead of tracking BottomCard height
- Menu scrim changed to fully transparent without fade animation

### Fixed

- First-frame flicker by deferring row height until measured
- Calendar height jitter when collapsing to week view
- Swipe interpolation discontinuity during month transitions
- Collapse drag not tracking finger (now uses dynamic dragRange)
- BottomCard positioning during collapse animation
- Flash when expanding after navigating months in collapsed state
- Week number baseline alignment stability during AnimatedContent transitions
- Year view stale year display on enter
- Year view missing scale animation on first launch
- "Today" button title bar jitter: replaced conditional rendering with alpha fade
- Folded state cross-month date not grayed out in week view
- Theme switching transparency issues by adding explicit background colors
- Predictive back gesture failure and end-of-animation flash on certain devices
- Back animation residual transition eliminated with `snapTo`
- Icon colors now adapt to `MaterialTheme` instead of hardcoded white

### Removed

- MonthHeader click-to-toggle-year-view (replaced by FAB menu)
- Year view arrow navigation (replaced by swipe gesture)
- Shift badge background circle for lighter visual weight
- Default ripple effect on DayCell (replaced by circular reveal animation)
- Aliyun Maven mirrors (switched back to Maven Central / Google)
- Unused Compose runtime ProGuard keep rules

---

[1.3.0]: https://github.com/xfy/yayacal/releases/tag/v1.3.0
[1.2.0]: https://github.com/xfy/yayacal/releases/tag/v1.2.0
[1.1.0]: https://github.com/xfy/yayacal/releases/tag/v1.1.0
[1.0.0]: https://github.com/xfy/yayacal/releases/tag/v1.0.0
