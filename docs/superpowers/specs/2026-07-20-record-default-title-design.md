# 新建记录标题预填默认值 — 设计规格

日期：2026-07-20

## 背景

「日期记录器」拍照后进入新建记录页（`RecordEditScreen`），标题初始为空，保存按钮禁用（`canSave = false`），用户必须手动输入标题才能保存。

## 目标

新建记录时标题预填默认值，用户进入页面即可直接点保存。

## 需求确认

- 默认标题格式：**拍摄日期**，`yyyy-MM-dd`（如 `2026-07-20`）。
- 联动规则：用户**未手动编辑过标题**时，修改「拍摄日期」标题跟随同步；手动编辑过后不再联动。
- 编辑模式（`recordId != null`）行为不变：标题来自已有记录，不参与联动。

## 方案

ViewModel 负责预填和联动（状态单一来源、可单测）。不采用 Screen 层 `LaunchedEffect` 预填（状态分散、难测）。

## 改动点

### `core/src/main/kotlin/plus/rua/project/RecordEditViewModel.kt`

1. `initNewRecord()`：`title` 预填为 `formatLocalDate(shootDate)`，`canSave = true`（标题非空 + 照片就绪，沿用现有校验规则）。
2. 新增私有标记 `private var titleManuallyEdited = false`：
   - `onTitleChange()` 中置 `true`（任何手动输入都算，包括清空）。
   - `onShootDateChange()` 中：标记为 `false` 时同步 `title = formatLocalDate(date)`。
   - `loadExistingRecord()` 加载成功后置 `true`（编辑模式已有标题不参与联动）。
3. 新增 `internal fun formatLocalDate(date: LocalDate): String`（`yyyy-MM-dd`），实现与现有 Screen 私有版本一致。

### `core/src/main/kotlin/plus/rua/project/ui/RecordEditScreen.kt`

1. 删除私有 `formatLocalDate`，改用 `RecordEditViewModel.kt` 中的 `internal` 版本，保证标题与「拍摄日期」按钮显示格式一致。
2. UI 结构、交互不变。

### 测试 `core/src/test/kotlin/plus/rua/project/RecordEditViewModelTest.kt`（新增）

覆盖用例：
- 新建模式：标题预填为拍摄日期格式，且 `canSave = true`。
- 未手动编辑：修改拍摄日期后标题跟随更新。
- 手动编辑后：修改拍摄日期标题不变。
- 手动清空标题：`canSave = false`。

测试需要 fake/stub `DateRecorderRepository`，参照现有 `DateRecorderSortTest.kt` 的测试写法。

## 边界情况

- 用户手动清空标题 → 保存按钮禁用（与现有校验一致）。
- 编辑模式：加载已有记录标题时同时将 `titleManuallyEdited` 置为 `true`，确保已有标题不会被拍摄日期联动覆盖（与"编辑模式不参与联动"的需求一致）。

## 验证

```bash
./gradlew :core:testDebugUnitTest --tests "plus.rua.project.RecordEditViewModelTest"
./gradlew spotlessApply
./gradlew :app:assembleDebug
```
