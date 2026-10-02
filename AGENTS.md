# Repository Guidelines

YaYa (鸭鸭日历) combines a Kotlin/Jetpack Compose lunar/shift calendar with a Rust Moments backend.

## Project Structure & Architecture

- `core/src/main/kotlin/plus/rua/project/`: Compose UI (`ui/`), ViewModels, business logic, repositories and Room.
- `app/src/main/`: Activity shells, manifest and themes. Keep business logic in `:core`; navigate with Activities and Intents.
- `core/src/test/kotlin/`: JVM tests; `core/src/main/assets/`: animations and media.
- `macrobenchmark/`: startup benchmarks and baseline profiles.
- `server/`: Axum/SQLx backend, migrations and integration tests; see `server/README.md`.

## Build, Test, and Development Commands

Use JDK 21 and the Gradle wrapper. From repository root:

```bash
make build                     # Build debug APK
./gradlew :app:installDebug     # Build/install on a device or emulator
make emulator                  # Start/reuse Pixel_10 and forward backend access
make server                    # Start backend using server/.env
make test                      # Run core JVM tests
make test T=CalendarUtilsTest   # Run one test class
make fmt                       # Apply Spotless/ktlint formatting
./gradlew spotlessCheck         # Check formatting without edits
```

Bare `make` builds the release APK. `make help` lists targets.

## Coding Style & Naming Conventions

Use four-space Kotlin indentation, PascalCase for classes/Composables and camelCase for functions/properties. Keep UI text Chinese. Use `kotlinx-datetime`; `java.util.Calendar` is forbidden.

Public Composables require KDoc explaining parameters and callback timing. Put `Modifier` last; prefix callbacks with `on`. Use zero-elevation `Card(onClick = …)` for clickable list items. Expose ViewModel state through read-only `StateFlow`. Explain deprecation suppressions inline. Run `make fmt` before committing Kotlin changes.

## Testing Guidelines

Use Kotlin test/JUnit 4 and `kotlinx-coroutines-test`. Name files `*Test.kt` and methods `method_condition_result`. Add regression coverage for changed logic; inject clocks for deterministic date tests. JVM tests require no emulator; validate UI changes on-device. After each feature, attempt `./gradlew :app:installDebug`; record environmental failures and continue.

Backend checks:

```bash
cargo fmt --manifest-path server/Cargo.toml --check
cargo clippy --manifest-path server/Cargo.toml --locked -- -D warnings
cargo test --manifest-path server/Cargo.toml --locked
bash server/tests/run.sh
```

Integration tests use disposable PostgreSQL through Docker.

## Commit & Pull Request Guidelines

Commit each completed feature/fix separately using `feat:`, `fix:`, `refactor:`, `docs:`, `test:` or `chore:`; optional scopes include `fix(server):`. Inspect `git status` and stage only relevant files. Routine commits may use `main`; branch for review. Android releases use `vX.Y.Z`; backend releases use `server-vX.Y.Z`. See `README.md` and `server/README.md` for release preparation and CI publishing.

PRs should explain behavior changes, link relevant issues, report validation and include screenshots for UI changes.

## Backend & Configuration Rules

Keep credentials in untracked `server/.env`. Append migrations; never rewrite applied migrations. Capture Moments accounts explicitly for every operation; never infer published authors from the selected account. Do not guess authors when importing legacy data.
