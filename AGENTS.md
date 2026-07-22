# Repository Guidelines

Guide for AI assistants working in **YaYa (鸭鸭日历)** — a pure-Android Jetpack Compose lunar/shift calendar app. Remote: `github.com/DefectingCat/yayacal` (+ `git.rua.plus` mirror). UI text is Chinese; date logic is `kotlinx-datetime` only (`java.util.Calendar` is banned).

> Note: `CLAUDE.md` is a symlink to this file. `COMMENTS.md` is referenced from several docs but does not exist yet — treat the KDoc rules in [Code Conventions](#code-conventions--common-patterns) as authoritative until it is created.

## Project Overview

YaYa is a single-app Android calendar focused on: Chinese lunar calendar, solar terms (节气) and traditional festivals (via **tyme4kt**), a personal WORK/OFF shift cycle, month/week/year views with infinite paging, and a photo-journal ("date recorder") feature with an in-app photo editor. Latest release `1.3.0` (see `CHANGELOG.md`); base version lives in `gradle.properties` (`app.version.base`).

## Architecture & Data Flow

**Three Gradle modules** (`settings.gradle.kts`, typesafe accessors on):

| Module | Type | Responsibility |
|--------|------|----------------|
| `:core` | `com.android.library` | **All** Compose UI, ViewModels, business logic, Room data layer |
| `:app` | `com.android.application` | Thin shell: Activities + Manifest + theme. **Iron rule: no business logic here.** |
| `:macrobenchmark` | `com.android.test` | Baseline Profile / Startup Profile generation |

Dependency chain: `:app` → `:core`; `:macrobenchmark` → `:app`.

**Navigation is Activity + Intent, NOT Compose Navigation.** Each screen has a 1:1 Activity in `:app` that just does `setContent { YaYaTheme { SomeScreen() } }`. `core/.../ui/DateRecorderNav.kt` centralizes the Intent-extra contract (`EXTRA_TEMP_PHOTO_PATH`, `EXTRA_FINAL_PHOTO_PATH`, `EXTRA_RECORD_ID`). Slide/fade transitions come from `app/.../BaseActivity.kt`.

**State pattern (all ViewModels):** private `MutableStateFlow` backing fields exposed as read-only `StateFlow` via `asStateFlow()`; many aggregate into a single `uiState: StateFlow<UiState>` through `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)`. The aggregation is an explicit design goal to minimize Compose recomposition. Tests read `stateFlow.value` directly (no Turbine).

**Home-screen data flow:** `CalendarViewModel` owns `selectedDate`, `isCollapsed`, `collapseProgress` (0=month↔1=week), `isYearView`, `shiftPattern`. It pulls lunar data from `LunarCache` (LRU `LinkedHashMap` + coroutine `Mutex`) which builds `DayCellInfo` per cell from tyme4kt `SolarDay`. `CalendarMonthView` lifts the shared `PagerState` and renders `CalendarPager` → `CalendarMonthPage` → `DayCell`.

**Persistence:** Room (`DateRecordDatabase` v1, `exportSchema = true` → `core/schemas/`). Repository stores relative photo paths under `filesDir/Pictures/date_recorder/` and deletes photos on record delete. Non-DB prefs (shift pattern, date checker) use SharedPreferences with **custom string encoding** (no JSON dependency) in `*Storage.kt`.

## Key Directories

```
app/src/main/
  kotlin/plus/rua/project/   # Activities (shell only): MainActivity, BaseActivity, *Activity
  AndroidManifest.xml        # Activity registry; SplashActivity present but disabled
  res/anim/, res/values/     # slide transitions, themes (Theme.Material.* NoActionBar)
core/src/main/
  kotlin/plus/rua/project/         # ViewModels, business logic, data, storage, tracing
  kotlin/plus/rua/project/ui/      # All Composable screens + calendar grid + nav contract
  kotlin/plus/rua/project/ui/theme/  # YaYaTheme
  assets/animations/*.webp         # scanned at config time → BuildConfig.WEBP_FILES
  baseline-prof.txt / baselineProfiles/startup-prof.txt  # generated profiles
core/src/test/kotlin/plus/rua/project[/ui]/   # the only unit tests
macrobenchmark/src/main/java/.../baseline/    # StartupBenchmark, BaselineProfileGenerator
scripts/                          # profile.sh, analyze-trace.sh, resize_duck_icon.py
```

**Namespace quirk:** `:core`'s `android.namespace` is `plus.rua.project.shared`, so core's `R` and `BuildConfig` live there — but the code package is `plus.rua.project`.

## Development Commands

```bash
./gradlew :app:assembleDebug                  # build debug APK
./gradlew :app:installDebug                   # install to device/emulator
./gradlew :core:testDebugUnitTest             # all unit tests (JVM, no emulator)
./gradlew :core:testDebugUnitTest --tests "plus.rua.project.ui.CalendarUtilsTest"  # one class
./gradlew spotlessApply                       # format (ktlint) — run before committing
```

Profiling & profiles (device required):
```bash
./gradlew :macrobenchmark:updateBaselineProfile        # generates + copies both profiles into :core
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest  # benchmarks only (no copy)
./scripts/profile.sh                                   # Perfetto trace, default 8s → logs/
./scripts/profile.sh --scenario month_browse --trace 15  # specific scenario, trace build
./scripts/profile.sh --list-scenarios                  # list named scenarios
./scripts/analyze-trace.sh [logs/trace_*.perfetto-trace]  # post-hoc SQL analysis
```

## Workflow

- **每完成一个功能点，提交一次。** 不要攒着最后一起提交。一个"功能点"是一个可独立说明的小块改动（一个 bug 修复、一个 Composable、一组测试、一次重构），而不是整次会话的全部产出。
- **提交粒度与信息由 AI 自主决定**：自行判断该功能点属于 `feat` / `fix` / `refactor` / `docs` / `test` / `chore`，写清摘要；改动小则合并提交，改动跨多步则在每步落盘。
- 只暂存本次功能点相关的文件，不要 `git add .` 把无关改动（如会话前遗留的脏文件）一并带入。提交前看一眼 `git status`。
- 当前在 `main` 分支：按仓库惯例可以直接在 `main` 上提交常规改动；如改动涉及发布或需评审，先开分支。
- **每写完一个功能后跑 `./gradlew :app:installDebug`** 做一次真机/模拟器冒烟安装，验证能编过、能装上。**允许失败**：无连接设备、无模拟器、签名/环境问题等导致失败属正常，不必阻塞后续工作——记下失败原因即可继续。这条是为了在能装上时尽早暴露问题，不是硬性 gate。

## Code Conventions & Common Patterns

- **KDoc required** on every public `@Composable`: document parameters and *when callbacks fire*. (This is the missing `COMMENTS.md` contract.)
- **`Modifier` always last** in Composable signatures.
- **Callbacks use `on` prefix** (`onDateClick`, `onDragEnd`).
- **Clickable list items:** use `Card(onClick = …)` with `CardDefaults.cardElevation(defaultElevation = 0.dp)` — **not** bare `Modifier.clickable()`.
- **`@Suppress("DEPRECATION")`** must carry an inline comment explaining why (currently used for `monthNumber`).
- **Dates:** `kotlinx-datetime` everywhere. `java.util.Calendar` is forbidden.
- **Testability seams:** `Clock` is constructor-injectable (tests pass a `FixedClock`); SharedPreferences are faked with in-memory impls written per-test-file (duplicated deliberately to avoid same-package private-class name clashes).
- **Pagers:** `HorizontalPager` with `pageCount = { Int.MAX_VALUE }` centered at `START_PAGE = Int.MAX_VALUE/2`; page↔month via `pageToYearMonth`/`yearMonthToPage` in `ui/CalendarUtils.kt`; sync via `snapshotFlow { pagerState.settledPage }.drop(1)`.
- **Trace markers:** `ComposeTrace.kt` wraps `android.os.Trace`, gated by `BuildConfig.ENABLE_TRACE` (on in `debug`/`trace`, off in `release`). Key markers: `MonthView:Compose`, `YearView:Compose`, `CalendarPager:Page:<year>-<month>`, `WeekPager:Page`, `VM:collapseProgress:*`, `YearGridView:*`. Full catalog in `DEVELOPMENT.md`.
- **Formatting:** Spotless + ktlint cover `src/**/*.kt` and root `*.gradle.kts` (not `.toml`/`.md`/scripts). `.editorconfig` permits PascalCase `@Composable` function names.

## Important Files

Central / load-bearing source (paths relative to repo root):

- `core/src/main/kotlin/plus/rua/project/CalendarViewModel.kt` — home-screen state: collapse math, shift resolution, ISO week, grid generation. The heart of the app.
- `core/src/main/kotlin/plus/rua/project/ui/CalendarMonthView.kt` — home Composable: month↔year transition, FAB menu, pager wiring (largest UI file).
- `core/src/main/kotlin/plus/rua/project/ui/CalendarUtils.kt` — all page↔date math + layout constants (`START_PAGE`, `COLLAPSE_THRESHOLD`).
- `core/src/main/kotlin/plus/rua/project/LunarCache.kt` — tyme4kt integration; produces `DayCellInfo` (the per-cell render contract).
- `core/src/main/kotlin/plus/rua/project/ShiftPattern.kt` — shift resolution (`kindAt`: overrides → rephaseFlips → active-anchor cycle). Most rigorously tested file.
- `core/src/main/kotlin/plus/rua/project/ui/CalendarPager.kt`, `ui/WeekPager.kt` — the two infinite pagers.
- `core/src/main/kotlin/plus/rua/project/ui/CalendarMonthPage.kt`, `ui/DayCell.kt` — per-page grid + cell rendering with collapse animation.
- `core/src/main/kotlin/plus/rua/project/ui/YearGridView.kt` — year overview grid.
- `core/src/main/kotlin/plus/rua/project/PhotoProcessor.kt` + `PhotoEditorState.kt` + `PhotoEditorViewModel.kt` — photo editor pipeline (load/rotate/crop/render with `HandStroke` overlay).
- `core/src/main/kotlin/plus/rua/project/{DateRecord,DateRecordDao,DateRecordDatabase,DateRecordConverters,DateRecorderRepository}.kt` — Room data layer for the photo journal.
- `core/src/main/kotlin/plus/rua/project/ComposeTrace.kt` — perf tracing shim.
- `core/src/main/kotlin/plus/rua/project/ui/DateRecorderNav.kt` — Intent extra keys (cross-Activity contract).
- `app/src/main/kotlin/plus/rua/project/{MainActivity,BaseActivity}.kt` — entry point + transition base.
- `gradle/libs.versions.toml` — all dependency versions.
- `app/build.gradle.kts` — build types, dynamic versioning (`baseVersion_gitHash_buildDate`).

Other docs: `DEVELOPMENT.md` (perf/profile workflow + trace marker catalog), `README.md` (user intro), `CHANGELOG.md` (Keep-a-Changelog; `[Unreleased]` + `[1.3.0] - 2026-07-09`). Module rules: `app/AGENTS.md`, `core/AGENTS.md`, `macrobenchmark/AGENTS.md`.

## Runtime/Tooling Preferences

- **JDK 17** (`VERSION_17` in all modules). Gradle wrapper **9.5.1**.
- **AGP 9.2.1 · Kotlin 2.3.21 · KSP 2.3.10 · Compose BOM 2026.06.01.** compileSdk/targetSdk **37**, minSdk **24**.
- Key libs: **kotlinx-datetime 0.8.0 · tyme4kt 1.5.0 · sketch 4.4.0 · zoomimage 1.6.0 · Room 2.8.4 · lifecycle 2.11.0 · activity-compose 1.13.0 · CameraX 1.5.3 · Media3 1.6.1.**
- **Gradle caches on:** configuration cache + build cache + parallel (`gradle.properties`). R8 full mode enabled.
- **Build types:** `debug` (default, trace on) · `release` (R8 + resource shrink, trace off, debug-signed) · `trace` (release + trace markers) · `benchmark` (release base, **no** minify — so generated profile names aren't obfuscated).
- **Profiling needs a device/emulator** with GPU acceleration (software renderer can't produce `gfxinfo` framestats). Real benchmarks need a physical device on a release target.
- No CI/CD exists — all builds, tests, profiling, and releases run locally.

## Testing & QA

- **All automated tests are pure-JVM unit tests** in `core/src/test/kotlin/plus/rua/project[/ui]/` — **no `androidTest` source set, no Compose UI tests, no Robolectric/Turbine/Mockk.** Run on JVM 17, no emulator.
- **Frameworks:** `kotlin-test-junit` (+ JUnit 4 in a few classes) and `kotlinx-coroutines-test` (`runTest`). `androidx.room:room-testing` is declared but unused.
- **Covered well:** date math (`CalendarUtilsTest`), the shift engine (`ShiftPatternTest` — cycle/override/`RephaseFlip`), `CalendarViewModel` observable state + grid generation, storage round-trips, record sorting, lunar birthday/rose-day flags, `PhotoProcessor.calculateInSampleSize`, `HandStroke` segmentation.
- **Coverage gaps to be aware of:** no Compose UI/instrumented tests (UI exercised only via the macrobenchmark journey); Room DAO/Database/migration logic untested; `Flow` emission sequences untested (only synchronous `.value` snapshots); `DateRecordDetailViewModel`/`PhotoEditorViewModel` and the edit-record path of `RecordEditViewModel` have no dedicated tests.
- **Test conventions:** classes `*Test.kt`; methods `method_condition_result`; in-memory SharedPreferences fakes written per file; fixtures inline (no shared helpers). Comments/KDoc are in Chinese and state scope explicitly.
- **Macrobenchmark** (`macrobenchmark/src/main/.../baseline/`) provides the only on-device coverage — `StartupBenchmark` (cold start) and `BaselineProfileGenerator` (drives a 12-step user journey to emit `baseline-prof.txt` + `startup-prof.txt`).

## Release Process

Driven by the `yayacal-release` skill (`.agents/skills/yayacal-release/SKILL.md`). Flow: bump `app.version.base` in `gradle.properties` + `versionCode` in `app/build.gradle.kts` → update `CHANGELOG.md` from `git log <last-tag>..HEAD` → commit exactly `release: vx.y.z` → lightweight tag `vx.y.z` → push `main --tags` → `assembleRelease` → `gh release create` with extracted notes. Release only from clean `main`; keep debug signing (no release keystore). Note: the skill's doc links to `github.com/xfy/yayacal` — the actual remote is `DefectingCat/yayacal`.
