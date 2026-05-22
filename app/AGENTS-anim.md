<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-05-22 -->

# anim

## Purpose
Activity 转场动画资源目录。定义 Activity 进入和退出的滑入/滑出动画效果，配合 `overridePendingTransition()` 使用。

## Key Files

| File | Description |
|------|-------------|
| `slide_in_right.xml` | 从右侧滑入（duration: 350ms，fast_out_slow_in 插值器） |
| `slide_in_left.xml` | 从左侧滑入 |
| `slide_out_left.xml` | 向左侧滑出 |
| `slide_out_right.xml` | 向右侧滑出 |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 新增转场动画需同步更新对应 Activity 的 `overridePendingTransition()` 调用
- 动画时长建议 300-400ms，使用 `fast_out_slow_in` 插值器保持 Material Design 一致性

## Dependencies

### Internal
- `app/src/main/kotlin/plus/rua/project/AboutActivity.kt` — 使用 slide_in_right / slide_out_left
- `app/src/main/kotlin/plus/rua/project/LicensesActivity.kt` — 使用 slide_in_right / slide_out_left

<!-- MANUAL: -->
