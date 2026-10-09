# 经期记录工具设计

## 背景

工具页目前有日期检查器、纪念日、日期记录器、朋友圈和五线谱练习。本设计新增「经期记录」：记录每次经期的起止日期、每天的心情与备注，并据此预测下次经期和易孕期。

数据不区分小白 / 小鸡毛账号，整份记录与后端同步，两台手机看到同一份数据。

## 范围

一期：

- 工具页新增「健康与生活」分组和「经期记录」入口卡片。
- 首页：状态卡、可左右滑动的月历、周期摘要。
- 单日面板：经期开关、心情、备注。
- 批量编辑：在月历上逐天标记或取消经期，用于补录和纠错。
- 历史页：列出每次经期的起止、天数和周期长度，可修改、删除。
- 设置页：默认周期长度、默认经期长度、黄体期长度，随文档同步。
- 离线照常记录，联网后自动同步，同步状态可见。
- 后端新增 `GET /api/v1/period` 和 `PUT /api/v1/period`。

二期：

- 历史页顶部的周期长度趋势图。
- 主日历日格底部的经期标记，以及底部卡片的经期提示。开关只存本机，默认关闭。

不做：

- 提醒通知。
- 流量、痛感、症状、体温等记录。
- 按账号区分数据、记录操作者。
- 「清除全部数据」。数据由两台手机共享，整份清空的风险大于收益；需要时在历史页逐条删除。

## 方案选择

同步采用「整份文档 + 修订号 + 客户端重放操作」：

- 服务器保存一份完整文档和一个递增修订号。客户端提交整份新文档时带上它所基于的修订号，修订号不一致就返回 409。
- 客户端在本机保存的不是改后的数据，而是用户做过的语义操作，例如「10 月 9 日标为经期」「10 月 9 日心情为平静」。遇到 409 时拉取最新文档，把这些操作重新应用一遍再提交。

理由：

- 数据量小。每年约 12 段经期、最多几百条备注，整份传输只有几十 KB。
- 经期区间彼此关联：不能重叠，相邻的连续天要合并成一段。一次编辑可能同时改动多段，整份替换天然是原子的，不存在中间态。
- 两台手机改的是不同日期或不同字段时，重放后两边的修改都保留，不需要用户处理冲突。改的是同一处时，后提交的覆盖先提交的。

备选方案（未采用）：

- 按单条记录增删改并用请求 ID 防重（朋友圈的做法）：合并或拆分区间需要多次请求，且有先后顺序依赖；离线积累的请求遇到 409 时难以自动处理。
- 只在线、不支持离线（朋友圈的做法）：断网时无法记录，不适合每天都可能用到的记录工具。
- 本地 Room 数据库：服务器才是数据来源，本机只需要缓存一份快照和待同步操作，用 SharedPreferences 就够，与 `ShiftPatternStorage`、`MomentsStorage` 保持一致。

## 数据模型

```kotlin
enum class PeriodMood(val key: String, val label: String) {
    HAPPY("happy", "开心"),
    CALM("calm", "平静"),
    TIRED("tired", "疲惫"),
    LOW("low", "低落"),
    IRRITABLE("irritable", "烦躁"),
}

/** 一次经期；end 为 null 表示进行中。区间以 start 作为身份。 */
data class PeriodRange(val start: LocalDate, val end: LocalDate?)

/** 某天的心情与备注，与是否在经期无关。 */
data class PeriodNote(val date: LocalDate, val mood: PeriodMood?, val text: String)

data class PeriodSettings(
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lutealLength: Int = 14,
)

data class PeriodDocument(
    val revision: Long,
    val settings: PeriodSettings,
    val ranges: List<PeriodRange>,
    val notes: List<PeriodNote>,
)
```

不变量（客户端用 `normalize()` 维护，服务端负责校验）：

- `ranges` 按 `start` 升序排列，相互不重叠，也不相邻。相邻的连续天合并为一段。
- 最多一段进行中，且必须是最后一段。
- 已结束的区间不超过 31 天。
- 所有日期都在 2000-01-01 至 2100-12-31 之间。
- 每天最多一条 `notes`。`mood` 和 `text` 至少有一个非空，两者都为空时删除该条。`text` 不超过 500 个字符。
- 设置的取值范围：周期 15–60 天，经期 2–10 天，黄体期 10–16 天。

区间互不重叠，所以 `start` 本身就能唯一标识一段，不再另设 UUID。文档不记录操作者：数据不分账号，也不从当前选中的朋友圈账号推断。

## 后端

### 迁移 `0004_periods.sql`

```sql
CREATE TABLE period_state (
    id boolean PRIMARY KEY DEFAULT true CHECK (id),
    revision bigint NOT NULL DEFAULT 0,
    cycle_length smallint NOT NULL DEFAULT 28 CHECK (cycle_length BETWEEN 15 AND 60),
    period_length smallint NOT NULL DEFAULT 5 CHECK (period_length BETWEEN 2 AND 10),
    luteal_length smallint NOT NULL DEFAULT 14 CHECK (luteal_length BETWEEN 10 AND 16)
);
INSERT INTO period_state DEFAULT VALUES;

CREATE TABLE period_ranges (
    start_date date PRIMARY KEY,
    end_date date CHECK (end_date >= start_date)
);

CREATE TABLE period_notes (
    day date PRIMARY KEY,
    mood text CHECK (mood IN ('happy', 'calm', 'tired', 'low', 'irritable')),
    text text NOT NULL DEFAULT '' CHECK (char_length(text) <= 500),
    CHECK (mood IS NOT NULL OR text <> '')
);
```

区间不重叠由 Rust 在写入前校验，不引入 `btree_gist` 扩展：每次都是整份替换，表里不会残留其他行。

### 接口

两个接口都不要求 `X-Account-ID`。

`GET /api/v1/period` 返回整份文档：

```json
{
  "revision": 7,
  "settings": { "cycle_length": 28, "period_length": 5, "luteal_length": 14 },
  "ranges": [
    { "start": "2026-09-12", "end": "2026-09-16" },
    { "start": "2026-10-08", "end": null }
  ],
  "notes": [
    { "date": "2026-10-08", "mood": "tired", "text": "有点腰酸" }
  ]
}
```

`PUT /api/v1/period` 的请求体为 `{ "base_revision": 7, "settings": …, "ranges": …, "notes": … }`，使用 `deny_unknown_fields`：

- 200：返回修订号为 8 的完整文档。
- 409 `{"error": "经期记录已在其他设备更新"}`：`base_revision` 不是当前修订号。
- 400：违反任一不变量（区间重叠或相邻、多段进行中、未知心情、备注过长、设置越界、日期越界），`error` 字段给出中文原因。
- 413：请求体超过 Axum 默认的 2 MiB 上限。

实现：

1. 在一个事务里先执行 `SELECT revision FROM period_state FOR UPDATE`，与 `base_revision` 比对。
2. 删除全部区间和备注，用 `UNNEST` 批量插入新数据，修订号加 1。
3. 两个基于同一修订号的并发 PUT 只有一个成功，另一个得到 409。

字段校验写成纯函数 `validate(&PeriodInput)`，由 Rust 单元测试覆盖。

### 文档与发布

- README 接口表增加这两个接口，并注明数据不分账号、不需要 `X-Account-ID`。
- `server/CHANGELOG.md` 的 Unreleased 下记录新增接口。
- 后端需要先于 Android 发布。Android 连接旧后端时 GET 返回 404：提示「服务器版本较旧，暂不能同步经期记录」，本机照常记录，后端升级后自动推送。

## Android 同步

### 本地存储 `PeriodSyncStorage`

使用 SharedPreferences `period_sync`，键名包含服务地址，按地址隔离。朋友圈草稿也是按服务地址隔离的。

- `snapshot`：最近一次从服务器取到的文档，用 org.json 序列化。
- `pending`：待同步的操作列表，每条带一个本机递增序号。

切换服务地址后，读到的是新地址自己的快照和待同步操作，不会把一台服务器的数据推到另一台。

### 操作 `PeriodOp`

所有操作都是幂等的纯函数 `apply(document, op)`，应用后统一调用 `normalize()`。

| 操作 | 触发 | 语义 |
| --- | --- | --- |
| `StartPeriod(date)` | 经期来了 | 新增一段进行中的区间。已有进行中的区间，或 `date` 已落在某段内时，不做改动 |
| `EndPeriod(date)` | 经期结束、补记结束日 | 把进行中区间的 `end` 设为 `date`；没有进行中的区间时不做改动 |
| `SetPeriodDay(date, isPeriod, today)` | 单日开关、批量编辑 | 先把进行中的区间按 `today` 展开为 `[start, today]`，再设置这一天，合并或拆分区间，最后把结束于 `today` 的那段恢复为进行中 |
| `UpdateRange(oldStart, start, end)` | 历史页修改 | 找不到 `oldStart`（已在别处改动）时不做改动；与其他区间重叠的部分合并 |
| `DeleteRange(start)` | 历史页删除 | 删除该段；不存在时不做改动 |
| `SetNote(date, mood, text)` | 单日面板关闭时 | 心情和备注都为空时删除这天的记录 |
| `UpdateSettings(settings)` | 设置页 | 整体覆盖设置 |

界面显示的数据 = 把 `pending` 依次应用到 `snapshot` 上；没有快照时从空文档开始。

### 同步流程 `PeriodRepository.sync()`

1. GET 最新文档，记为 `server`。
2. `pending` 为空：保存 `snapshot = server`，结束。
3. 计算 `next = pending.fold(server, apply)`。如果 `next` 的内容与 `server` 相同（例如上次 PUT 已成功、只是响应丢失），保存 `snapshot = server`，清除这批操作，结束。
4. 提交 `PUT(base_revision = server.revision, next)`：
   - 200：保存 `snapshot` 为响应内容。只清除这次参与重放的操作；请求期间新增的操作保留，随后再同步一次。
   - 409：回到第 1 步，最多重试 3 次。
   - 网络失败或 5xx：保留 `pending`，状态为「同步失败」。
   - 404：状态为「服务器版本较旧」。
   - 400：说明客户端和服务端的校验不一致，属于 bug。保留 `pending`，显示错误原因，并提供「放弃未同步的修改」入口，不自动丢弃数据。

触发时机：

- 页面 `onResume`。
- 每次本机操作之后。同一时间只运行一次同步；同步进行中又有新操作时，结束后再补一次。
- 点击同步状态手动重试。

不做后台同步，也不定时轮询。

顶栏右侧显示同步状态：已同步、同步中、N 项待同步、同步失败（点击重试）、服务器版本较旧。

`PeriodRepository` 是进程内单例，对外暴露 `StateFlow`。三个 Activity 共用同一个实例，所以从设置页或历史页返回首页时数据已经是最新的。

### 网络

`HttpPeriodApi` 沿用 `HttpMomentsRepository` 的 OkHttp 超时设置和 `{"error": …}` 错误解析，请求不带 `X-Account-ID`。服务地址取 `MomentsConnection.url()`，与朋友圈共用同一个连接设置。

## 预测 `PeriodPredictor`

`PeriodPredictor` 是纯函数。输入是规范化后的文档和外部注入的 `today`；输出 `PeriodForecast`，包含：当前状态、未来 3 段预测经期、对应的易孕期和排卵日、周期长度、经期长度、规律性。

- 周期长度：取最近 6 个完整周期（相邻两次的开始日之差），去掉短于 15 天或长于 60 天的（多半是漏记），取中位数。不足 2 个周期时用设置里的默认值。
- 经期长度：最近 6 段已结束经期的天数取中位数，结果限制在 2–10 天；没有已结束的经期时用默认值。
- 下次经期 = 最近一次开始日 + 周期长度，并向后再推 2 段。
- 排卵日 = 预测开始日 − 黄体期；易孕期为排卵日前 5 天至后 1 天。只对当前周期和预测周期计算，不回溯历史周期。
- 规律性：至少要有 3 个完整周期，否则显示「记录不足」。按最近周期长度的极差（最长减最短）分三档：≤ 3 天为规律，4–7 天为较规律，超过 7 天为不规律。
- 界面文案写「周期约 29 天」「经期约 5 天」，不写「平均」，以免和中位数不符。

当前状态：

| 状态 | 条件 | 主文案 | 主按钮 |
| --- | --- | --- | --- |
| `NoData` | 没有任何区间 | 还没有记录 | 记录上次经期（日期选择） |
| `InPeriod(day, expectedEnd)` | 有进行中区间，且不超过 10 天 | 经期第 N 天 | 经期结束 |
| `ForgotToEnd(day)` | 进行中区间超过 10 天 | 是否忘记结束？ | 补记结束日 |
| `Normal(daysUntil, phase)` | 没有进行中区间，今天早于预测开始日 | 距下次经期 N 天；处于易孕期或排卵日时副文案标出 | 经期来了 |
| `Late(days)` | 没有进行中区间，今天已晚于预测开始日 | 已推迟 N 天 | 经期来了 |

推迟时，日历不再显示已经过期的那段预测，后续预测从明天开始顺延。

## 界面

### 工具页入口

在「记录与回忆」和「学习与练习」之间新增「健康与生活」分组，复用 `ToolCard`：

- 标题：经期记录
- 标签：周期预测
- 描述：记录经期与心情，预测下次经期和易孕期
- 图标：`Icons.Outlined.WaterDrop`
- testTag：`tool_period`

### 首页 `PeriodTrackerScreen`

```
┌────────────────────────────────┐
│ ‹  经期记录         已同步   ⚙   │
├────────────────────────────────┤
│  ╭──────╮   经期第 3 天          │  状态卡，环形进度 = 当前周期进度
│  │ ◔ 3  │   预计 10月12日 结束    │
│  ╰──────╯       [ 经期结束 ]     │
├────────────────────────────────┤
│ 今天  ☺ ☻ ☹ ☹ ☹      写备注 ›    │  点击心情直接记录；备注打开单日面板
├────────────────────────────────┤
│        ‹   2026年10月   ›  [编辑] │
│  一  二  三  四  五  六  日        │
│  ●   ●   ●   ·   ·   ·   ·       │  ● 已记录经期  ◌ 预测经期
│  ·   ▢   ▢   ▢   ◆   ▢   ·       │  ▢ 易孕期      ◆ 排卵日
│  ·   ·   ·   ·   ·   ·   ·       │  日期下方小圆点 = 当天有心情或备注
│  ·   ◌   ◌   ◌   ◌   ◌   ·       │
│  ● 经期  ◌ 预测  ▢ 易孕期  ◆ 排卵日  │
├────────────────────────────────┤
│ 周期约 29 天 · 经期约 5 天 · 规律  › │  进入历史页
└────────────────────────────────┘
```

月历使用 `HorizontalPager`，参考 `ShiftCalendarGrid` 的写法。

### 单日面板 `PeriodDaySheet`

点击日历上的某一天弹出 `ModalBottomSheet`：

- 标题为日期，例如「10月9日 周五」。
- 经期开关：生成 `SetPeriodDay`。只允许今天及以前的日期。
- 心情：五个选项单选，再点一次取消。
- 备注：多行输入，不超过 500 字，显示字数。
- 面板关闭时，心情或备注有变化才生成一条 `SetNote`，不会每输入一个字就生成一次操作。

### 批量编辑

点击「编辑」后月历进入编辑模式：

- 点击今天及以前的日期即标记或取消经期，每次点击生成一条 `SetPeriodDay`。
- 未来的日期不可点。
- 点击「完成」退出编辑模式。

### 历史页 `PeriodHistoryScreen`

- 按时间倒序列出每次经期：开始日期、经期天数，以及到下一次开始的周期天数。
- 每行用一条横条示意：经期部分为玫红色，其余为灰色。
- 点击一行打开日期区间选择，生成 `UpdateRange`。
- 删除需要二次确认，生成 `DeleteRange`。
- 列表项使用零阴影的 `Card(onClick = …)`。

### 设置页 `PeriodSettingsScreen`

- 默认周期长度、默认经期长度、黄体期长度，使用步进器调整，生成 `UpdateSettings`。
- 说明文字：记录不足 2 个周期时，按这里的默认值预测。
- 免责声明：「预测基于历史记录，仅供参考，不能作为避孕或医疗依据」。

### 颜色

应用使用系统动态取色，但经期相关颜色需要固定且一眼能认出。新增 `PeriodColors`，亮色和暗色各一套：玫红表示经期，浅紫底表示易孕期，深紫表示排卵日。预测经期使用与已记录经期相同的玫红色，画成虚线圆环。具体色值在真机上调整。其余卡片和文字继续使用 `MaterialTheme`。

## 文件清单

`:core`：

- `PeriodDocument.kt`：数据类、`PeriodMood`、`normalize()`。
- `PeriodOp.kt`：操作和 `apply()`。
- `PeriodPredictor.kt`：预测。
- `PeriodApi.kt`：接口定义和 `HttpPeriodApi`。
- `PeriodSyncStorage.kt`：快照与待同步操作的持久化。
- `PeriodRepository.kt`：同步流程与 `StateFlow`。
- `PeriodViewModel.kt`：首页、历史页、设置页共用，修改都转成 `PeriodOp`。
- `ui/PeriodTrackerScreen.kt`、`ui/PeriodCalendarGrid.kt`、`ui/PeriodDaySheet.kt`、`ui/PeriodHistoryScreen.kt`、`ui/PeriodSettingsScreen.kt`。
- `ui/PeriodComponents.kt`：`PeriodColors` 配色、日期文案、日期选择对话框、同步状态文案。
- `ui/ToolsScreen.kt`：新增分组和入口。

`:app`：

- `PeriodTrackerActivity`、`PeriodHistoryActivity`、`PeriodSettingsActivity`，并在 `AndroidManifest.xml` 中注册。
- `ToolsActivity` 增加跳转。

`server/`：

- `migrations/0004_periods.sql`
- `src/periods.rs`
- `src/main.rs`：注册路由。
- `tests/api.py`：新增集成检查。
- `README.md`、`CHANGELOG.md`

## 测试

Android JVM 测试：

- `PeriodDocumentTest`：`normalize()` 的排序、合并相邻和重叠区间、去重、空备注删除。
- `PeriodOpTest`：每种操作的语义与幂等性，展开、拆分、恢复进行中区间，基于旧数据重放时不做改动。
- `PeriodPredictorTest`：无记录、只有 1 段、规律、有异常值、推迟、经期进行中、忘记结束、易孕期边界。
- `PeriodSyncStorageTest`：序列化往返、按服务地址隔离。
- `PeriodRepositoryTest`：使用假 API 覆盖成功、409 后重放成功、响应丢失后重放无变化、请求期间新增的操作被保留、网络失败保留 `pending`、404 旧后端、400 不丢数据。
- `HttpPeriodApiTest`：JSON 解析与错误映射。

后端：

- Rust 单元测试：`validate()` 的每条规则。
- `tests/api.py`：初始空文档、PUT 后读回、修订号冲突 409、并发相同修订号只成功一个、各类 400、不带 `X-Account-ID` 也能访问。

每完成一项功能，执行 `./gradlew :app:installDebug`，在模拟器上验证两台设备（切换服务地址或清除数据模拟）的同步效果。

## 提交计划

1. `feat(server): 新增经期记录同步接口`
2. `feat: 新增经期记录工具`
3. 二期另行提交。

## 风险

- 后端没有鉴权：任何能访问接口的人都能读写经期数据，与朋友圈当前的访问模型一致。一期决定沿用这个模型，并在 README 中写明；需要更严格的保护时，再另加一个共享访问密钥。
- 每次同步都传输整份文档：备注多年累积后请求会变大。2 MiB 大约能容纳十年的每日备注，到时可以把备注拆成按日期单独提交。
