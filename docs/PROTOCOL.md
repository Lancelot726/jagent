# work 目录文件协议

`jagent` 把「任务过程」写成磁盘上的协议文件，而不是只留在对话上下文里。这样做的目的有三条：

1. **压缩不丢事实** —— 上下文被折叠后，事实仍在文件里，模型可随时 `recall`。
2. **重启可续跑** —— 目标写在 `plan.md`，下次用 `--resume` 接上，不用重述任务。
3. **过程可审计** —— 每一步走了哪个工具、产生了什么结论，落成日志与计划，肉眼可查。

本文件是该协议的唯一权威描述：文件叫什么、谁写谁读、什么格式、什么规则不可破。

## 1 目录布局

运行时目录是**工作根目录下的 `work/`**（工作根由 `--cwd` 指定，默认当前目录）：

```
<workspace>/
├── work/
│   ├── plan.md          计划与进度快照（覆盖写）
│   ├── facts.md         长期事实（整体重写）
│   ├── summary.md       压缩摘要（覆盖写）
│   ├── episodes.log     情节流水（只追加）
│   ├── artifacts/       命名产物目录
│   │   └── <name>
│   └── jagent.properties  持久化的用户选择（语言等）
├── <业务文件>            工具在 workspace 内创建的文件
└── ...
```

`work/` 已在 `.gitignore` 中，属运行时产物，不进版本库。

## 2 抽象文件名系统

五个协议件各有**固定抽象名**，不随任务、轮次、时间变化。所有读写都走常量，代码里不存在字面量拼路径。

| 抽象名 | 文件名 | 写入者 | 读取者 | 写模式 |
|---|---|---|---|---|
| `PLAN` | `plan.md` | `Graph.savePlan` | `Graph.planGoal`（`--resume`） | 覆盖 |
| `FACTS` | `facts.md` | `Memory.remember` / `forget` | `Memory`（构造时回读）、`recall` 工具 | 整体重写 |
| `SUMMARY` | `summary.md` | `Memory.summarize`（压缩时触发） | `Memory`（构造时回读）、`recall` 工具 | 覆盖 |
| `EPISODES` | `episodes.log` | `Memory.episode` | 人（审计） | 只追加 |
| `ARTIFACTS` | `artifacts/` | `save_artifact` 工具 | `read_artifact` 工具 | 按名覆盖 |

**规则**：抽象名一旦确定，跨版本不改。新增协议件 = 在 `WorkDir` 加一个常量 + 在此表登记，不改既有名字。

## 3 格式定义

### 3.1 `plan.md`

人可读的进度快照，每轮结束后由 `Graph.savePlan` 覆盖写：

```
# plan

goal: <本轮目标，换行折成空格>
updated: <ISO-8601 时间戳>
nodes: <总数> done: <完成数> failed: <失败数>
tokens: in=<输入 token> out=<输出 token>

## steps

- <state>  <kind>  <label>  | <note>
- ...
```

`steps` 段一行一个执行图节点，`state` ∈ `running` / `done` / `failed`，`kind` ∈ `agent` / `tool` / `compress` / `mem` / `gate` / `human`。

**续跑协议**：`--resume` 只认 `goal: ` 这一行 —— `Graph.planGoal` 逐行扫描，返回第一个 `goal: ` 之后的文本。因此

- `goal:` 行是**续跑的唯一契约**，其格式变更必须同步 `planGoal`；
- 其余行（`updated` / `nodes` / `steps`）对人有用，对续跑无用，可自由扩展。

### 3.2 `facts.md`

长期事实，模型通过 `remember` 追加、`forget` 删除：

```
# facts

- <事实一>
- <事实二>
```

**规则**：
- 表头固定为 `# facts`（内部件，走 L1 英文，见 §5）。
- 每条事实单行；写入时换行被折成空格，首尾空白剔除；空事实被忽略。
- 上限 **200 条**，超出从最旧的一端淘汰（FIFO）。
- 文件被**整体重写**，不是追加：模型忘记一条，磁盘上即消失。

### 3.3 `summary.md`

被压缩掉的上下文的摘要，由压缩触发时覆盖写：

```
# summary

<摘要正文>
```

摘要正文由 `Compressor.digest` 生成，取对话中的目标、用过的工具、最近结论三段，再拼上当时的记忆摘要：

```
goal: <本次目标>
tools: <去重后按出现顺序的工具名>
last: <最近一条非空 assistant 结论>
```

**已知限制**：摘要会把**上一版摘要**（经 `Memory.digest()`）再次并入，因此连续多轮压缩时 `summary.md` 会逐轮累积、缓慢增长。短任务无感；长任务下这是本轮已知的待优化点（已在 `loop/R1_92_logs.txt` 登记）。

### 3.4 `episodes.log`

只追加的情节流水，一行一条：

```
[<步号>] <角色> <单行事件>
```

- 步号：`0` 记目标，其余为 ReAct 轮次号。
- 角色：`goal` / `assistant` / `tool:<工具名>` / `compress`。
- 事件折成单行，超过 120 字符截断并加 `…`。
- **永不重写**，是审计的唯一保真来源。

### 3.5 `artifacts/`

命名产物的落脚点，对应两个工具：

| 工具 | 参数 | 行为 |
|---|---|---|
| `save_artifact` | `name` + `content` | 写入 `work/artifacts/<name>` |
| `read_artifact` | `name` | 读回，超过 40000 字符截断 |

**名字规则（安全约束）**：

1. 名字为空或纯空白 → 拒绝（`artifact name required`）。
2. 反斜杠统一成正斜杠。
3. 所有 `../` 片段被**剥除**，不报错、不逃逸。
4. 归一化后**必须仍在 `work/artifacts/` 之内**，否则拒绝（`bad artifact name`）。
5. 读不存在的产物 → `no such artifact: <name>`。

### 3.6 `jagent.properties`

`Config.save` 写出的持久化选择，目前只有一项：

```
user-lang=zh
```

解析优先级（`Config.resolveLang`）：`--user-lang` > `JAGENT_USER_LANG` > `work/jagent.properties` > 首启交互选择 > 默认 `zh`。文件同时被通用配置读取器扫描，因此也可放 `provider` / `model` / `budget` 等键。

## 4 文件生命周期

| 时机 | 动作 |
|---|---|
| `Memory` 构造 | 建 `work/`、`work/artifacts/`；三个文本件**仅在不存在时**播种（`fact.md` 与 `summary.md` 播种表头，`episodes.log` 播种空文件） |
| 每次 `run` | 追加情节；压缩时写 `summary.md`；结束时覆盖写 `plan.md` |
| `remember` / `forget` | 整体重写 `facts.md` |
| `--resume` 启动 | 读 `plan.md` 的 `goal:` 取得任务，无需命令行给任务 |

**关键纪律：播种只在文件缺失时发生**，因此重跑不会清掉已有记忆 —— 这是「重启续跑」能成立的前提。

## 5 语言分层（硬约定）

`work/` 是**内部件**，读者是模型与人（审计），因此：

- **表头与框架生成的标签走 L1**：固定英文、极强缩略（`# facts`、`goal:` / `tools:` / `last:`、`[compressed context] folded=N`）。
- **面向用户的界面文本走 L2**：全部在 `src/main/resources/lang/<code>.properties`，由 `Lang` 按用户选定语言装配。
- 模型自己写入的事实内容不限语言（那是模型的输出，不是框架的文本）。

也就是说：**框架写进 `work/` 的每一个字都应是英文**；中文只出现在用户能看到的界面里。

## 6 与其他约束的关系

- 路径安全：所有文件工具在 `resolve` 后必须 `startsWith` 工作根，越界即返回错误文本而不抛异常（见 `docs/DEMO.md` §8）。
- 语言槽位：`work/jagent.properties` 的语言选择与提示词模板中的 `{{user_lang}}` 是同一口径的两个落点。
- 版本管理：`work/` 一律不进 git；协议件本身不在仓库里，只有本文档在。
