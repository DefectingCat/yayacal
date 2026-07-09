## 目标

修复班次设置页的 6 个问题:
1. 长按撤销误删巧合重合的独立断点
2. 长按产生的 override 与 phaseBreak 解耦,单独点翻转留幽灵重排
3. 连续长按产生断点链,撤销老断点不级联
4. `kindAt` 每格每次重组重算(无 remember 缓存)
5. "恢复默认"无撤销,立即落盘
6. 角标颜色硬编码 `onPrimary`、固定 6 行

根因:问题 1-3 都是"长按产生的 override + phaseBreak 是两个独立数据,但语义上是原子操作"。彻底解法是重构数据模型。

## 总体方案

**引入 `RephaseFlip` 原子结构**,把"翻转某天 + 从次日起重排"绑定成一个不可分割的数据单元。`overrides`(纯单日翻转)和 `phaseBreaks` 两字段保留,但长按操作改为产出 `RephaseFlip` 而非拆成两个独立字段。

### 新数据模型

```kotlin
// 新增:原子化的"翻转并重排"操作记录
data class RephaseFlip(
    val date: LocalDate,        // 被翻转的天
    val flippedTo: ShiftKind,   // 翻转后的值
    val rephaseFrom: LocalDate  // 重排起点(= date + 1)
)

data class ShiftPattern(
    val anchorDate: LocalDate,
    val cycle: List<ShiftKind>,
    val overrides: Map<LocalDate, ShiftKind> = emptyMap(),  // 保留:纯单日翻转(点)
    val rephaseFlips: List<RephaseFlip> = emptyList(),      // 新增:翻转并重排(长按)
    val name: String = "默认"
) {
    fun kindAt(date: LocalDate): ShiftKind? {
        if (cycle.isEmpty()) return null
        // 1. 纯单日翻转优先
        overrides[date]?.let { return it }
        // 2. rephaseFlips 中被翻转的当天
        rephaseFlips.find { it.date == date }?.let { return it.flippedTo }
        // 3. 找活跃锚点:rephaseFlips 的 rephaseFrom <= 当天 的最大那个;无则基础锚点
        val (anchor, offset) = activeAnchor(date)
        ...
    }
}
```

**关键变化**:
- `phaseBreaks` 字段**删除**,替换为 `rephaseFlips`
- `activeAnchor` 改为从 `rephaseFlips` 取 `rephaseFrom` 作锚点
- kindAt 优先级:overrides → rephaseFlip 当天 → 活跃锚点 cycle

**撤销语义变干净**:长按 7/10 产生 `RephaseFlip(7/10, OFF, 7/11)`。再次长按 7/10 → 按 `date == 7/10` 精确匹配整个原子记录删除,**不会误删**别的(解决问题 1)。单独点 7/10 翻转时,如果存在 `rephaseFlip.date == 7/10`,提示或阻止(解决问题 2)。

### 存储格式

App 未正式发布(git 历史显示个人项目,SharedPreferences 刚引入)。**不做向后兼容迁移**:
- 存储格式改:`KEY_BREAKS` → `KEY_REPHASE`,编码 `日期:flippedTo:rephaseFrom`(`1/0:ISO日期`)
- 旧数据(含 `KEY_BREAKS` 的)在 `load()` 时解析 `KEY_REPHASE` 失败返回 null → 回退 `DEFAULT_PATTERN`(load 已有 try/catch)
- 用户唯一数据是默认 2班2休,丢失无感知

### 测试策略(先建安全网)

**Task 0 优先**:在改模型前,先用端到端测试锁定当前所有行为(kindAt 的全部输出),确保重构后这些断言仍然成立。新增针对长按场景(翻转+重排、撤销、连续长按)的测试,先在旧模型上跑过(记录当前行为),再重构后验证不回归。

## 实施步骤(7 个 Task,TDD)

### Task 0: 建立测试安全网(不改产品代码)

在改任何产品代码前,补充测试覆盖当前行为,作为重构的回归基线:
- `ShiftPatternTest`:补充"长按翻转+重排"端到端断言(用 override+phaseBreak 组合模拟当前长按产出,断言后续序列)。包括:单次长按、连续两次长按、撤销场景。
- `ShiftPatternStorageTest`:补充往返测试覆盖 phaseBreak。
- 这些测试在旧模型上**必须先通过**,记录为"重构前行为"。

### Task 1: 重构 ShiftPattern 数据模型(RephaseFlip)

- 新增 `RephaseFlip` data class
- `ShiftPattern` 删除 `phaseBreaks`,加 `rephaseFlips: List<RephaseFlip>`
- 重写 `kindAt`:overrides → rephaseFlip 当天 → 活跃锚点(从 rephaseFlips.rephaseFrom 取)
- 重写 `activeAnchor`:从 `rephaseFlips` 取 `rephaseFrom <= date` 的最大
- 删除 `PhaseBreak` 类
- **所有 Task 0 测试必须通过**(行为不变)
- 补充新测试:rephaseFlip 撤销精确匹配、多个 rephaseFlip 共存

### Task 2: 重构 ShiftPatternStorage 存储格式

- `KEY_BREAKS` → `KEY_REPHASE`
- save/load 改为序列化 `rephaseFlips`(`日期:flippedTo:rephaseFrom`)
- `parseRephase` 替代 `parseBreaks`
- 往返测试更新

### Task 3: 重写 ShiftCalendarGrid 交互逻辑

- `toggleFlipAndRephase`:产出 `RephaseFlip(date, flippedTo, date+1)`,撤销按 `date` 精确匹配
- `togglePhaseBreak` 删除
- `toggleOverride` 加保护:若该天存在 rephaseFlip,移除整个 rephaseFlip(防止幽灵重排,解决问题 2)
- 角标 `isRephaseStart` 改为检测 `rephaseFlips.any { it.rephaseFrom == date }`
- ShiftDayCell 加 `remember(pattern, date) { pattern.kindAt(date) }` 缓存(解决问题 4)

### Task 4: 修复角标颜色(问题 6)

- 角标文字色按类别:班=`onPrimary`、休=`onError`、起=`onTertiary`
- 对齐 DayCell 风格:9sp、TopEnd、CircleShape 胶囊背景

### Task 5: 修复固定行数(问题 7)

- `ShiftCalendarGrid` 改用 `getMonthGridInfo(viewYear, viewMonth).rows` 动态算行数
- 删除硬编码 `(0 until 6)`,改为 `(0 until rows)`

### Task 6: "恢复默认"加撤销(问题 5)

- 引入 `SnackbarHost`(项目首次使用,需在 Scaffold 加 `snackbarHost` 参数)
- 点恢复默认 → 存旧 pattern 到临时变量 → 显示 Snackbar "已恢复默认,撤销" 5 秒
- 点撤销 → 恢复旧 pattern
- 不再用 AlertDialog 确认(Snackbar 撤销比确认对话框更现代,且防误操作)

### Task 7: 全量验证

- `./gradlew :app:assembleDebug :core:testDebugUnitTest` 通过
- `./gradlew spotlessApply`
- 手动验证清单(模拟器):长按翻转重排、再次长按撤销、连续长按、恢复默认撤销

## 改动文件清单

| 文件 | Task | 改动 |
|------|------|------|
| `core/.../ShiftPattern.kt` | 1 | 删 PhaseBreak,加 RephaseFlip,重写 kindAt |
| `core/.../ShiftPatternStorage.kt` | 2 | 存储格式改 |
| `core/.../ui/ShiftCalendarGrid.kt` | 3,4,5 | 交互逻辑+角标颜色+动态行数 |
| `core/.../ui/ShiftPatternScreen.kt` | 6 | Snackbar 撤销 |
| `core/test/.../ShiftPatternTest.kt` | 0,1 | 安全网+重构后验证 |
| `core/test/.../ShiftPatternStorageTest.kt` | 0,2 | 往返测试更新 |

## 风险

- **kindAt 行为变化**:`rephaseFlips` 的 `rephaseFrom` 与旧 `phaseBreaks.date` 在"长按场景"下语义等价(都是次日),但"纯 phaseBreak"(offset!=0)不再支持。当前 UI 不产生 offset!=0 的断点(长按总是 offset=0),所以无实际影响。Task 0 测试会验证。
- **存储不兼容**:旧 `KEY_BREAKS` 数据被忽略,用户重置为默认。已确认可接受(个人项目,数据可重建)。
- **Snackbar 首次引入**:需确认 Material3 Scaffold 的 snackbarHost 用法正确,不破坏现有布局。