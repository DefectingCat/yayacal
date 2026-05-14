# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

YaYa is a calendar app built with Kotlin Multiplatform (KMP) + Compose Multiplatform, targeting Android and iOS. The shared UI is written entirely in Compose Multiplatform with Material 3.

## Build Commands

```bash
# Build Android debug APK
./gradlew :androidApp:assembleDebug

# Run shared module tests
./gradlew :shared:allTests

# Run a single test class
./gradlew :shared:androidHostTest --tests "plus.rua.project.ComposeAppCommonTest"

# Build iOS app — open iosApp/ in Xcode and run from there
```

Gradle configuration cache and build cache are enabled by default (`gradle.properties`).

## Architecture

**Two-module structure:**
- `:shared` — all Compose UI, ViewModel, and business logic (KMP library)
- `:androidApp` — thin Android shell (`MainActivity` → `App()`)

iOS entry point is `MainViewController.kt` in `shared/src/iosMain/`, consumed by the Xcode project in `iosApp/`.

**Shared source sets:**
- `commonMain` — all Compose UI and ViewModel code
- `androidMain` — Android-specific platform impl + preview tooling
- `iosMain` — `ComposeUIViewController` factory

**Calendar UI composition** (all in `plus.rua.project.ui`):
```
CalendarMonthView          ← top-level screen (MonthHeader + WeekdayHeader + CalendarPager)
  ├── MonthHeader          ← year/month label + ISO week number
  ├── WeekdayHeader        ← fixed "一二三四五六日" row
  └── CalendarPager        ← HorizontalPager with Int.MAX_VALUE pages
        └── CalendarMonthPage  ← 6×7 grid of DayCell composables
              └── DayCell      ← single day circle with selection/today states
```

`CalendarViewModel` holds `selectedDate` state and computes month day grids + ISO week numbers. Week starts on Monday (ISO 8601).

**Pager page mapping:** `CalendarPager` uses `Int.MAX_VALUE` pages centered at `Int.MAX_VALUE / 2`. Page-to-yearMonth conversion is done arithmetically via `pageToYearMonth()` / `yearMonthToPage()` — no index-based list.

## Key Dependencies

- Kotlin 2.3.21, Compose Multiplatform 1.10.3, Material 3 1.10.0-alpha05
- `kotlinx-datetime` for all date logic (no java.util.Calendar)
- AGP 9.2.1, compileSdk/targetSdk 36, minSdk 24
- JVM target: 17

## Conventions

- Package: `plus.rua.project` (shared), `plus.rua.project.ui` (UI composables)
- Version catalog at `gradle/libs.versions.toml` — all dependency versions declared there
- `@Suppress("DEPRECATION")` used for `monthNumber` access on `kotlinx.datetime.LocalDate`
- UI text is in Chinese (weekday labels, month header format "2026年5月")
