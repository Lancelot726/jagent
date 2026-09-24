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
│   ├── runs.md          历次运行台账（整体重写）
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

六个协议件各有**固定抽象名**，不随任务、轮次、时间变化。所有读写都走常量，代码里不存在字面量拼路径。

| 抽象名 | 文件名 | 写入者 | 读取者 | 写模式 |
|---|---|---|---|---|
| `PLAN` | `plan.md` | `Graph.savePlan` | `Graph.planGoal`（`--resume`） | 覆盖 |
| `RUNS` | `runs.md` | `Cli` 每轮收尾 | `Memory`（构造时回读）、`recall` 工具 | 整体重写 |
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

### 3.3 `runs.md`

**历次运行台账**，每次 `run` 收尾时由 `Cli` 追加一条，是「本地记忆」里**唯一自动写入**的一项——不依赖模型主动调用 `remember`：

```
# runs

- <yyyy-MM-dd HH:mm> goal=<目标，超 80 字符截断> tools=<名字:次数，逗号分隔> steps=<步数> result=<done|limit|failed(原因)>
```

例：

```
- 2026-09-20 02:07 goal=demo tools=spawn_agent:2,write_file:2,read_file:1 steps=4 result=done
```

**规则**：

- 表头固定 `# runs`（内部件，走 L1）。
- 单行一条；目标折成空格并截断，原因取错误消息首段（超 40 字符截断）。
- 上限 **20 条**，超出从最旧的一端淘汰（FIFO）。
- 文件**整体重写**，与 `facts.md` 同理。
- 失败与中止的运行**同样记账**（`result=failed(...)`），因为「上一次没跑成」正是下一次最需要知道的事。

**为什么单独一个文件**：`episodes.log` 记的是**一轮之内**的每一步，`runs.md` 记的是**跨轮**的每一次尝试。前者粒度细、无限增长，后者粒度粗、固定 20 条——把后者塞进前者，回灌时就得先把前者读一遍再筛，代价与噪声都不划算。

### 3.4 `summary.md`

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

### 3.5 `episodes.log`

只追加的情节流水，一行一条：

```
[<步号>] <角色> <单行事件>
```

- 步号：`0` 记目标，其余为 ReAct 轮次号。
- 角色：`goal` / `assistant` / `tool:<工具名>` / `compress`。
- 事件折成单行，超过 120 字符截断并加 `…`。
- **永不重写**，是审计的唯一保真来源。

### 3.6 `artifacts/`

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

### 3.7 `jagent.properties`

`Config.save` 写出的持久化选择，目前只有一项：

```
user-lang=zh
```

解析优先级（`Config.resolveLang`）：`--user-lang` > `JAGENT_USER_LANG` > `work/jagent.properties` > 首启交互选择 > 默认 `zh`。文件同时被通用配置读取器扫描，因此也可放 `provider` / `model` / `budget` / `max-steps` 等键。

## 4 文件生命周期

| 时机 | 动作 |
|---|---|
| `Memory` 构造 | 建 `work/`、`work/artifacts/`；四个文本件**仅在不存在时**播种（`runs.md` / `facts.md` / `summary.md` 播种表头，`episodes.log` 播种空文件） |
| 每次 `run` | 追加情节；压缩时写 `summary.md`；结束时覆盖写 `plan.md`；**收尾追加一条 `runs.md`** |
| `remember` / `forget` | 整体重写 `facts.md` |
| `--resume` 启动 | 读 `plan.md` 的 `goal:` 取得任务，无需命令行给任务 |

**关键纪律：播种只在文件缺失时发生**，因此重跑不会清掉已有记忆 —— 这是「重启续跑」能成立的前提。

## 5 记忆的两条契约

「本地记忆」在这个项目里指两件**互不替代**的事，各有一条契约：

| 契约 | 载体 | 生命期 | 谁负责 |
|---|---|---|---|
| **落盘回灌** | `work/` 的 `runs.md` / `facts.md` / `summary.md` | 跨进程、跨天 | 框架自动记账 + 模型 `remember` |
| **会话内上下文** | 进程内的对话消息列表 | 只在本进程内 | 交互式命令行逐行传递 |

**落盘回灌**：`Memory` 构造时把三个文件读回内存，`digest()` 拼成一段文本，由 `Agent.assembleSystem()` 注入系统消息。因此**新进程的第一次模型调用就已经知道过去发生了什么**，不需要模型先想起来去 `recall`。

`digest()` 的顺序固定为：`summary` → `earlier runs, newest last` → `facts`。三块都可能为空；全空时返回空串，系统提示里不留空标题。

**会话内上下文**：交互式命令行把每次成功运行的「任务 + 回答」压成两条消息，交给下一次运行（`Agent.prior`）。**只传递结论，不传递中间的工具噪声**——中间步骤已经在 `episodes.log` 里，需要时用 `recall` 取。

**两条契约的分工**：会话内上下文让**同一会话的第 2 次提问**不必重述任务；落盘回灌让**关掉重开之后的第 1 次提问**不必重述任务。只做前者，重启即失忆；只做后者，同一会话里模型仍看不见刚刚那个问题的答案。原口径见 `loop/R3_90_req_constitution.md` 修订 5。

## 6 语言分层（硬约定）

`work/` 是**内部件**，读者是模型与人（审计），因此：

- **表头与框架生成的标签走 L1**：固定英文、极强缩略（`# facts`、`# runs`、`goal:` / `tools:` / `steps:` / `result:`、`[compressed context] folded=N`）。
- **面向用户的界面文本走 L2**：全部在 `src/main/resources/lang/<code>.properties`，由 `Lang` 按用户选定语言装配。
- 模型自己写入的事实内容不限语言（那是模型的输出，不是框架的文本）。

也就是说：**框架写进 `work/` 的每一个字都应是英文**；中文只出现在用户能看到的界面里。

## 7 与其他约束的关系

- 路径安全：所有文件工具在 `resolve` 后必须 `startsWith` 工作根，越界即返回错误文本而不抛异常（见 `docs/DEMO.md` §8）。
- 语言槽位：`work/jagent.properties` 的语言选择与提示词模板中的 `{{user_lang}}` 是同一口径的两个落点。
- 版本管理：`work/` 一律不进 git；协议件本身不在仓库里，只有本文档在。
- 呈现剥离：`Text.plain` 只作用于**终端显示**（`Cli` 打印答案时）。`work/` 与对话上下文里保留模型的原文，因此协议件的内容不因显示层而改变。
- 本机环境：系统提示里的 `os` / `shell` / `cwd` / `console encoding` 四项事实由 `Env` 提供（见 `loop/R3_90_req_constitution.md` 修订 7）。`shell` 决定 `bash` 工具的调用方式，`console encoding` 决定工具输出的解码方式——两者必须同源，否则输出会乱码。
- 步数上限：`max-steps` 是唯一的循环出口，根 agent 与子 agent 共用同一个值（`SpawnTool` 透传）。`0` 或负数表示不限，此时**没有第二道硬闸**——`Budget` 只记账、`Governor` 的熔断只影响报价，都不中止循环。因此「不限步数」这一档只承诺不给自设的截止，不承诺能兜住不收敛的任务。
- 收尾的唯一落点：`run` 的最后一屏是 `answer` 段，**成功 / 失败 / 中止三种结局都在这一段内交付**（失败给「停在第几步 + 原因 + 建议」，中止给「截至第几步没有最终答复」）。不存在第二个总结块——早先的「本轮总结」四行块已整体删除，其键族（`rep.*`）也从语言包里移除。
- 机制自证默认静默：报价行 `pick`、末行 `[quota ...]`、收尾的 `summary` 对账块同为运维视角，只在 `--scorecard` 打开时打印。默认输出只保留 `step` / `tool` / `result` 三类过程行与 `answer` 段。工具参数按 `path` / `command` / `task` / `pattern` / `name` / `url` 取一个值摘要显示（截 60 字符），原始 JSON 不上屏。
- 记账与呈现分离：`--scorecard` 关掉只是**不打印**，`work/scorecard.txt` 与 `work/runs.md` 的写入不受影响（`run.answer` 的剥离同样只作用于显示层，见上一条）。
