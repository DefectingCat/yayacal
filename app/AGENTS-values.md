<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-05-22 -->

# values

## Purpose
Android 值资源目录，包含应用字符串和 Material 3 主题定义。

## Key Files

| File | Description |
|------|-------------|
| `strings.xml` | 应用名称字符串（`app_name: YaYa`） |
| `themes.xml` | 日间主题：`Theme.Material.Light.NoActionBar` |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 新增字符串资源在此文件中定义
- 主题继承自 `Theme.Material.Light.NoActionBar`，支持 `enableEdgeToEdge()` 全屏显示
- 夜间模式主题在 `../values-night/themes.xml` 中定义

## Dependencies

### Internal
- `app/src/main/AndroidManifest.xml` — 引用主题

<!-- MANUAL: -->
