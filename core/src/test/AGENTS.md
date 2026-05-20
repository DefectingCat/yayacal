<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-20 | Updated: 2026-05-20 -->

# commonTest

## Purpose
共享模块的单元测试 source set，包含 ViewModel、班次模型和日历工具函数的测试。

## Key Files

无（目录仅包含子目录）

## Subdirectories

| Directory | Purpose |
|-----------|---------|
| `kotlin/plus/rua/project/` | 核心逻辑测试（见 `kotlin/plus/rua/project/AGENTS.md`） |
| `kotlin/plus/rua/project/ui/` | UI 工具函数测试（见 `kotlin/plus/rua/project/ui/AGENTS.md`） |

## For AI Agents

### Working In This Directory
- 测试使用 `kotlin.test` 和 molecule（Turbine）进行状态流断言
- 新增测试后运行 `./gradlew :shared:allTests`
- 测试类名遵循 `*Test.kt` 约定

## Dependencies

### External
- `kotlin-test`
- `kotlinx-coroutines-test`
- `molecule`（Turbine 风格的状态流测试）

<!-- MANUAL: -->
