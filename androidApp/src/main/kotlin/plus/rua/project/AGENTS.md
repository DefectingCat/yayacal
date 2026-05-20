<!-- Parent: ../../../../../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# project

## Purpose
Android 应用入口源码目录，仅包含 `MainActivity.kt`。

## Key Files

| File | Description |
|------|-------------|
| `MainActivity.kt` | Android 入口 Activity，继承 `ComponentActivity`，调用 `setContent { App() }` |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- `MainActivity.kt` 应保持极简，不要添加额外逻辑
- 所有 UI 和业务逻辑在 `:shared` 模块中

## Dependencies

### Internal
- `shared/src/commonMain/kotlin/plus/rua/project/App.kt` — `App()` Composable 入口

<!-- MANUAL: -->