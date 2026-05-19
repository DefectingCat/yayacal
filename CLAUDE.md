# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YaYa is a calendar app built with Kotlin Multiplatform (KMP) + Compose Multiplatform, targeting Android and iOS. The shared UI is written entirely in Compose Multiplatform with Material 3.

## Build Commands

```bash
# Build Android debug APK
./gradlew :androidApp:assembleDebug

# Install Android debug APK to connected device
./gradlew :androidApp:installDebug

# Run all shared module tests
./gradlew :shared:allTests

# Run shared module tests on Android host only
./gradlew :shared:testAndroidHostTest

# Run a single test class
./gradlew :shared:testAndroidHostTest --tests "plus.rua.project.ui.CalendarUtilsTest"

# Generate iOS framework (required before first Xcode open or after clean)
./gradlew :shared:generateDummyFramework

# Build iOS app — open iosApp/iosApp.xcworkspace in Xcode and run from there
```

Gradle configuration cache and build cache are enabled by default (`gradle.properties`).

## Architecture

**Two-module structure:**
- `:shared` — all Compose UI, ViewModel, and business logic (KMP library)
- `:androidApp` — thin Android shell (`MainActivity` → `App()`)

iOS entry point is `MainViewController.kt` in `shared/src/iosMain/`, consumed by the Xcode project in `iosApp/`.

**Shared source sets:**
- `commonMain` — all Compose UI and ViewModel code
- `commonTest` — shared tests (run via `:shared:allTests` or `:shared:androidHostTest`)
- `androidMain` — Android-specific platform impl + preview tooling
- `iosMain` — `ComposeUIViewController` factory

**Calendar UI composition** (all in `plus.rua.project.ui`):
```
CalendarMonthView          ← top-level screen (MonthHeader + WeekdayHeader + pager + BottomCard)
  ├── MonthHeader          ← year/month label + ISO week number
  ├── WeekdayHeader        ← fixed "一二三四五六日" row
  ├── CalendarPager        ← HorizontalPager with Int.MAX_VALUE pages (month view)
  │     └── CalendarMonthPage  ← 6×7 grid of DayCell with collapse animation
  │           └── DayCell      ← single day circle with selection/today states
  ├── WeekPager            ← HorizontalPager for single-week view (collapsed state)
  │     └── DayCell
  ├── YearGridView         ← 4×3 mini-month grid with year navigation (year view)
  │     └── MiniMonth      ← compact month: title + weekday row + day numbers
  └── BottomCard           ← drag handle card, drives collapse/expand gestures
```

`ShiftPattern` (in `plus.rua.project`) defines personal shift cycles (WORK/OFF) independent of public holidays. Uses modular arithmetic: `(date - anchorDate) mod cycle.size`.

`CalendarUtils` (in `plus.rua.project.ui`) holds pager constants (`START_PAGE = Int.MAX_VALUE/2`, `COLLAPSE_THRESHOLD = 0.25f`) and page↔date arithmetic (`pageToYearMonth`, `yearMonthToPage`, `pageToWeekMonday`).

**Collapse/expand animation:** `CalendarMonthView` supports month↔week transition via `CalendarViewModel.collapseProgress` (0f=month, 1f=week). `BottomCard` captures vertical drag gestures and calls `viewModel.onDrag()`/`onExpandDrag()`. When progress crosses 50% on release, a spring animation snaps to the nearest state. `CalendarMonthPage` compresses non-selected weeks toward zero height during collapse. When fully collapsed, `WeekPager` replaces `CalendarPager` for efficient single-week paging.

**Pager page mapping:** Both `CalendarPager` and `WeekPager` use `Int.MAX_VALUE` pages centered at `Int.MAX_VALUE / 2`. Page-to-date conversion is arithmetic — no index-based list. `CalendarPager` maps pages to yearMonth; `WeekPager` maps pages to week-Monday dates. Both skip the initial `snapshotFlow` emission (`.drop(1)`) to preserve the "today" selection on first render.

`CalendarViewModel` holds `selectedDate` and `isCollapsed` state, computes month day grids (6×7=42 cells) and ISO week numbers. Week starts on Monday (ISO 8601).

**Performance tracing:** `ComposeTrace.kt` provides `composeTraceBeginSection`/`composeTraceEndSection` via expect/actual — Android routes to `android.os.Trace`, iOS is a no-op. Custom markers are inserted at key points (e.g., `MonthView:Compose`, `YearView:Compose`, `VM:collapseProgress`) for Perfetto/Systrace analysis. See `DEVELOPMENT.md` for trace recording and Python parsing scripts.

## Key Dependencies

- Kotlin 2.3.21, Compose Multiplatform 1.11.0, Material 3 1.10.0-alpha05
- `kotlinx-datetime` 0.8.0 for all date logic (no java.util.Calendar)
- `tyme4kt` for Chinese traditional calendar (lunar dates, solar terms, festivals)
- `sketch` 4.4.0 for animated GIF display (`AsyncImage` with `sketch-animated-gif`)
- AGP 9.2.1, compileSdk/targetSdk 37, minSdk 24
- JVM target: 17
- R8 full mode enabled (`android.enableR8.fullMode=true`)

## Conventions

- Package: `plus.rua.project` (shared), `plus.rua.project.ui` (UI composables)
- Version catalog at `gradle/libs.versions.toml` — all dependency versions declared there
- `@Suppress("DEPRECATION")` used for `monthNumber` access on `kotlinx.datetime.LocalDate` — must include inline comment explaining reason
- UI text is in Chinese (weekday labels, month header format "2026年5月")
- Public `@Composable` functions require KDoc per `COMMENTS.md`
- `Modifier` parameter always last in composable signatures
- Callback parameters use `on` prefix (`onDateClick`, `onMonthChanged`)
