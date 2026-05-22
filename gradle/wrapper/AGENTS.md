<!-- Parent: ../../AGENTS.md -->
<!-- Generated: 2026-05-22 | Updated: 2026-05-22 -->

# wrapper

## Purpose
Gradle Wrapper 目录，包含 `gradle-wrapper.jar` 和 `gradle-wrapper.properties`。Wrapper 允许项目在没有预装 Gradle 的环境中使用固定版本的 Gradle 构建。

## Key Files

| File | Description |
|------|-------------|
| `gradle-wrapper.jar` | Gradle Wrapper 可执行 JAR |
| `gradle-wrapper.properties` | Wrapper 配置：Gradle 发行版 URL（当前 9.5.1） |

## Subdirectories
无

## For AI Agents

### Working In This Directory
- 通常不需要手动修改此目录内容
- 升级 Gradle 版本时使用 `./gradlew wrapper --gradle-version=X.Y.Z`
- `gradle-wrapper.properties` 中的 `distributionUrl` 指向 Gradle 官方发行版

## Dependencies

### External
- Gradle 9.5.1

<!-- MANUAL: -->
