# 演示脚本

本文是一份**照着敲就能看到效果**的演示清单。每节给出：命令 → 该看什么 → 实测输出片段。

设计理念在 `jagent` 里不是口号，而是运行时行为。因此演示的重点不是「功能能用」，而是**每条方法论机制怎么被肉眼观察到**。

## 0 准备

```bash
./mvnw -q package          # 产出 target/jagent.jar
java -jar target/jagent.jar version
```

所有演示默认走 `--provider mock`，**离线、零密钥、不烧 token**。MockClient 是一个脚本化的假模型，会自主写出「写两个文件 → 读回校验 → 收尾」的完整 ReAct 轨迹，因此无需网络即可观察全链路。

需要真模型时，把 `--provider openai --base-url <地址> --api-key <密钥> --model <名字>` 换上即可；密钥也可以走环境变量，**绝不进代码、不进 git**。

---

## 1 环境自检：终端到底支不支持你要画的东西

```bash
java -jar target/jagent.jar doctor
```

**看什么**：stdout 编码、控制台编码、终端尺寸，以及末尾的 UTF-8 探针与宽度校验。

```
jagent 0.1.0 · doctor
  java          25.0.4  (Oracle Corporation)
  系统          Windows 11 10.0 / amd64
  stdout 编码   GBK
  控制台编码    GBK
  终端尺寸      100x30
  模型通道      mock  离线模拟，无需密钥
  输出语言      zh / Zh

  UTF-8 探针  ├─ └─ │ ● ◐ ○ ✗ ✓ →  制表符
  宽度校验  状态▸○◐●  显示宽度=8 字符数=6
  警告: stdout 编码不是 UTF-8，制表符可能乱码。
```

**观察点**：在 Windows 中文控制台上，`stdout 编码` 会是 `GBK`，程序会主动告警。这不是崩溃，而是**如实报告**——同时程序已强制重建 UTF-8 输出流，所以制表符仍然正确。`宽度校验` 一行为东亚宽字符做了双宽修正（6 个字符显示宽度为 8），这是终端图能对齐的前提。

---

## 2 语言槽位：内部固定英文缩略，对外由用户选

```bash
java -jar target/jagent.jar help                   # 默认中文
java -jar target/jagent.jar help --user-lang eng   # 英文
```

**看什么**：同一份程序，界面语言随用户选择切换。

```
jagent 0.1.0 · terminal coding agent (pure JDK, zero deps)
usage: jagent <command> [options]
  doctor            inspect terminal environment and config
```

**观察点**：语言分**两层**——内部层（提示词、日志、`work/` 文件表头）永远是英文加极强缩略，因为它追求最短 token 且缩略语法只在英文成立；面向层由 `--user-lang` / `JAGENT_USER_LANG` / `work/jagent.properties` / 首启选择逐级解析。提示词里对应位置写作槽位 `{{user_lang}}`，装配时替换，**同一份模板服务所有语言**。

加一门语言 = 加一个 `lang/<code>.properties` + 一枚枚举，不动业务代码。

---

## 3 交互式命令行：不带参数启动，反复敲

```bash
java -jar target/jagent.jar
```

**看什么**：出现 `> ` 提示符，敲一行跑一行，直到 `exit`。

```
jagent 0.1.0 · 终端 coding agent（纯 JDK，零依赖）
输入任务后回车执行；:help 帮助，exit 退出

> :help
...
> 写一个演示文件
（完整的 ReAct 轨迹 + 收尾答案；要对账块则加 --scorecard 启动）
> exit
已退出
```

**观察点**：这是**基础版**——有输入循环，**没有** raw mode 行内编辑、没有方向键历史（那要自己处理跨平台按键字节，见 `R3_90` 修订 2）。`exit` / `quit` / `:q` 或 EOF 都能退出；管道里没有 TTY 时会读到 EOF 自然结束，不会挂死。

---

## 4 自主循环：模型自己决定调工具、看结果、再决定

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph
```

**看什么**：每轮打印「轮次 / 工具 / 结果」，直到模型自己收尾。

```
  step 1
  tool spawn_agent sub_write_alpha
  tool spawn_agent sub_write_beta
  result 完成：sub_write_alpha_a.txt 与 sub_write_alpha_b.txt 已写入并校验通过。
  result 完成：sub_write_beta_a.txt 与 sub_write_beta_b.txt 已写入并校验通过。

  step 2
  tool write_file demo/demo_a.txt
  tool write_file demo/demo_b.txt
  result wrote 16 bytes -> demo/demo_a.txt
  result wrote 15 bytes -> demo/demo_b.txt

  answer
完成：demo_a.txt 与 demo_b.txt 已写入并校验通过。

  [steps=4 nodes=26 in=1568 out=94 2648ms]
  计划已写入 <cwd>/work/plan.md
```

**观察点**：没有人在循环里控制流程。ReAct 的「想—做—看」由模型驱动，程序只提供工具与约束。任务完成后模型主动停止，把收尾答复打在 `answer` 段里，随后是 `[steps=... in=... out=...]` 这类可核算的统计。

**最后一屏就是 `answer`**，且三种结局都在这一段内交付：

```
  answer
    本轮中止于第 1 步：all 2 attempts failed: ConnectException
    建议 可先看 work/plan.md 与 work/episodes.log 里的中间结果。
```

- 成功：模型自己的收尾答复。
- 失败：停在第几步 + 一句原因 + 一条下一步建议——这是「运行结束后人不必回翻日志就知道发生了什么」的落点，也是 `work/runs.md` 那一行的来源（见 §10、§13）。
- 撞上步数上限：说明截至第几步没有得到最终答复。

也就是说，**过程行只有 `step` / `tool` / `result` 三类，收尾只有 `answer` 一段**。工具参数只摘要显示（路径、命令本身，截到 60 字符），不打印原始 JSON。早先版本在 `answer` 之后还叠过一个「本轮总结」四行块和一个蓝色对账块（八行，`summary`），把收尾挤成了三截——现已移除：总结块整体退役，对账块改由 `--scorecard` 显式索取：

```bash
java -jar target/jagent.jar run fanout --scorecard
```

**步数上限可配**。默认单轮 16 步，根 agent 与子 agent 共用同一个值；跑满即停，`answer` 段内写「已达最大步数」，`runs.md` 记 `result=limit`：

```bash
java -jar target/jagent.jar run <任务> --max-steps 0
```

`--max-steps 0`（或任意非正数）表示**不限步数**。此时循环没有内建的步数出口，收尾完全交给模型自己判断。要注意目前**没有第二道硬闸**：`--budget` 只记账并影响报价、熔断只影响把哪一档模型放进档位选择，两者都**不中止**循环。所以不限步数适用于「任务本身会收敛」的场合；模型若陷入反复调工具，会一直转下去。

---

## 5 多 agent 并行：图上真的分叉了

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph --tool-delay 300
```

**看什么**：父 agent 派发两个 `spawn_agent`，两个子 agent 在**同一时刻**各跑自己的 loop。加 `--tool-delay` 把并行窗口拉长，便于肉眼与计时确认。

**观察点**：子 agent 不是「串行排队」，而是共享同一个执行图与调度器，各自从图上长出自己的子树。P4T 断言两个子 agent 的工具区间**实测重叠**（各约 230ms），并在图上同时出现 `sub_write_alpha` 与 `sub_write_beta` 两条分支。

---

## 6 终端执行图：复杂任务被画成树

```bash
java -jar target/jagent.jar run fanout --cwd demo-run          # 真终端：底部实时重绘
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph  # 纯日志
```

**看什么**：真终端下，底部有一块实时执行图，节点状态用字形区分：运行中 `◐`、完成 `●`、失败 `✗`、等待 `○`；层级用 `├─ └─ │` 连接。

**观察点**：
- **只有一个写者**——渲染器只读图快照，不碰业务对象，避免并发绘制互相踩。
- **非 TTY 自动退化**——管道、重定向、CI 里不会喷满转义序列（P3T 断言：live 产 escape、非 live 不产）。
- 图块用「上移 n 行 + 清行」滚动重绘，不是全屏刷新，所以不会闪。

> 树形连接符是否错位、仪表盘是否闪烁，只能在真实终端确认——这正是本项目把 G3 送审定为「作者在本机终端实跑」的原因。

---

## 7 模型路由：用密封报价决定用哪个模型档位

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph --scorecard
```

**看什么**：加 `--scorecard` 后，每轮模型调用前打印一行报价结果：

```
  pick cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)
```

> 报价行、末行 `[quota ...]` 与收尾的 `summary` 对账块**同属机制自证，默认不开**（见 §4）。日常使用不必看到它们。

**观察点**：

- 两个档位（廉价 / 强力）各自**密封报价**，报价 = 估值 × 成功率 − 成本，互不可见。
- 采纳者按**次高报价**结算，所以 `pay` 等于落败者的报价（0.44），而不是自己的（0.51）。
- `+0.14` 是本次成交给系统带来的**净收益**（显示名 `net`），进预算记账。
- 估值里含两个信号：自适应并发控制给出的健康度 `valueScale`，以及预算压力 `usedFraction`——预算越紧，报价越低，越倾向廉价档。**这就是模型路由、自适应并发控制、资源管理三者在同一处交汇。**

---

## 8 资源管理：预算记账与压缩 ROI

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph --compress-after 64 --scorecard
```

> 注：默认 `--compress-after` 是 6000；mock 短任务上下文达不到，所以演示时压到 64 好让压缩**当场发生**。加 `--scorecard` 才有下面的预算行。

**看什么**：预算行 + 压缩行。

```
  compress 182 -> 16 tok  ctx 182 > 64, 204 rounds x 166 saved vs 16 cost -> ROI 2116.5x
  [quota 2031/20000 tok net 1.31 conc=8 err=0% ctl:up 7->8]
```

**观察点**：

- **预算记账**：输入、输出 token 都记账，剩余额度决定还能不能继续；`net` 来自上面报价成交的累计。
- **压缩不按阈值触发，按 ROI 决策**：到阈值只是「可以评估」，真正动手要算——
  `roundsLeft = 剩余预算 / 每轮消耗`，`benefit = 剩余轮数 × 每轮省下的 token`，`ROI = benefit / cost`，只有 `ROI > 1.5` 才压。
- 所以阈值只是门票，**账才算是否进场**。打印里把四个量都摊开了，可以当场复核。

---

## 9 自适应并发控制：并发度自己升降，出错就熔断

先看健康时的**闭环升并发**（上面第 8 节末行 `ctl:up 7->8`）：连续成功 → 并发逐级上调。

再看故障时的**熔断**——把 base_url 指向一个死端口：

```bash
java -jar target/jagent.jar run breaker --cwd demo-breaker \
  --provider openai --base-url http://127.0.0.1:9/v1 --api-key k --model m --no-graph
```

```
  pick cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)
  模型调用失败，已中止本轮: all 2 attempts failed: ConnectException
  [steps=1 nodes=2 in=0 out=0 164ms]
  [quota 0/5000 tok net 0.14 conc=1 err=49% ctl:down 2->1]
```

**看什么**：`err=49%`、`conc=1`、`ctl:down 2->1`。

**观察点**：

- **并发不是常量**，是控制回路里的**受控指标**：错误率做反馈调节，死区防止抖动，连续失败触发熔断，退避重试带抖动。
- `all 2 attempts failed` 说明主通道失败后**降级到备用档**也失败了——降级链本身就是可观察的一环。
- 控制器的每一次决策都留痕（`ctl:down 2->1`），不是黑盒。

---

## 10 记忆：`work/` 长出协议文件

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --provider mock
```

**看什么**：

```bash
find demo-run/work -type f
# demo-run/work/plan.md  runs.md  facts.md  summary.md  episodes.log  artifacts/
```

打开 `runs.md`：

```
# runs

- 2026-09-20 02:07 goal=fanout tools=spawn_agent:2,write_file:2,read_file:1 steps=4 result=done
```

打开 `plan.md`：

```
# plan

goal: fanout
nodes: 29 done: 29 failed: 0
tokens: in=1777 out=254

## steps

- done  agent  fanout  | done
- done  compress  compress 182->16 tok  | ctx 182 > 64, ... -> ROI 2116.5x
- ...
```

**观察点**：文件名是**抽象名系统**（`plan` / `runs` / `facts` / `summary` / `episodes` / `artifacts`），与任务、时间无关，规则见 `docs/PROTOCOL.md`。

`runs.md` 是其中**唯一由框架自动记账**的一项：不依赖模型主动调用 `remember`，每跑一次就多一行，失败与中止也照记。`episodes.log` 记的是**一轮之内**的每一步，`runs.md` 记的是**跨轮**的每一次尝试——粒度不同，所以分成两个文件。

压缩发生后 `summary.md` 会留下摘要，`episodes.log` 留下只追加的流水——**上下文被折叠了，事实仍在磁盘上**。

---

## 11 人类闸门：需要人拍板时，图上开一个 HUMAN 节点

模型可以调用 `ask_human` 工具把问题抛给人。在**交互终端**里它会等待输入；在无终端的管道里（例如本演示脚本）它**自动放行**并返回提示，不会把流程挂死：

```
  结果 auto-approved: no interactive console available, proceed with your best judgement.
```

**观察点**：这是「自动化 ≠ 无人化」的落点——自动化的是流程，人的决策点被显式建模成图上一个 `HUMAN` 节点（`kind=human`），而不是散落在某段 if 里。

---

## 12 断点续跑：从磁盘上的目标接着走

```bash
java -jar target/jagent.jar run --resume --cwd demo-run --provider mock --no-graph
```

**看什么**：不传任务描述，程序从 `work/plan.md` 的 `goal: fanout` 行取回目标并继续。

```
[gpt-4o-mini | gpt-4o-mini]

  轮次 1
```

**观察点**：续跑契约只有一行 —— `plan.md` 里的 `goal: `。其余（`updated` / `nodes` / `steps`）是给人看的，不影响续跑。这也意味着 `work/` 不能被清空，`sleep` 一夜回来仍能接上。

---

## 13 本机环境：模型不该猜自己在哪个 shell 上

```bash
java -jar target/jagent.jar run "现在几点" --cwd demo-run --no-graph
```

**看什么**：模型调用 `bash` 工具时的写法。系统提示里已经写死了四项事实：

```
## Local environment — facts
- os: Windows 11 10.0 amd64
- shell: cmd.exe /c
- cwd: <工作根>
- console encoding: GBK
Commands run in the shell above; it is not a unix shell unless the os says so.
stdin is closed, so a command that waits for input will hang until it is killed.
```

**观察点**：这一节记录的是**踩过的坑**。缺这段事实时，模型会写 `date '+%Y-%m-%d'`（POSIX 语法，`cmd.exe` 不认）或 `python3 -c ...`（本机是 `python`），然后：

- `cmd.exe` 的 `date` **不带 `/t` 是交互式命令**——它会等人输入新日期。原实现没有关子进程的 stdin，于是命令一直挂着，直到 10 秒超时被杀。现在 `start()` 之后立刻 `close()` 子进程 stdin，读到 EOF 即返回（实测 `date` 从 10 秒挂死变成 50 毫秒内返回，且不再需要 `/t`）。
- 输出按 **UTF-8 硬解码**，而 `cmd.exe` 吐的是 GBK，中文一律变成 `����`。现在按 `native.encoding`（本机为 `GBK`）解码；若解出替换字符 `U+FFFD`，再退回 UTF-8 试一次。R3T 两处都断言了。

两条修正合起来才成立：**shell 决定怎么调用，encoding 决定怎么解码**，两者必须同源。写在 `docs/PROTOCOL.md` §7。

---

## 14 记忆：跨进程与跨提问，是两条契约

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph   # 第一次
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph   # 换一个进程再跑
```

**看什么**：第二次运行启动时，`runs.md` 已经被读回并拼进系统提示。

```
summary: ...
earlier runs, newest last:
- 2026-09-20 02:07 goal=fanout tools=... steps=4 result=done
facts:
- ...
```

交互式命令行里逐行提问时，同一进程内还会额外传递「上一次的任务 + 结论」。**关掉重开之后仍不失忆**，靠的是前者。

**观察点**：这两件事必须分开说，因为它们的失效方式完全不同：

| 契约 | 载体 | 失效方式 |
|---|---|---|
| 落盘回灌 | `work/runs.md` / `facts.md` / `summary.md` | 只做会话内上下文的话，**一重启就失忆** |
| 会话内上下文 | 进程内的消息列表 | 只做落盘回灌的话，**同一会话里看不见刚问过的那句** |

早期实现两者都缺：`episodes.log` 只写不读，`facts` 只有模型主动 `remember` 才有内容，`summary` 只有压缩发生才写——而 mock 短任务 `compress: 0 events`，于是记忆永远是空的，第二次对话自然像失忆。现在 `runs.md` 由框架**每次都写**、启动时**每次都读**。

---

## 15 呈现：终端不该看到 Markdown 记号

```bash
java -jar target/jagent.jar run fanout --cwd demo-run
```

**看什么**：答案是**纯文本**——没有加粗记号、没有代码围栏、没有标题号。

```
  answer
完成：fanout_a.txt 与 fanout_b.txt 已写入并校验通过。
```

**观察点**：

- **剥离只发生在显示层**。`Text.plain` 只作用于 `Cli` 打印答案的那一刻；对话上下文和 `work/` 里保留的是模型原文。所以这是「终端观感」的修正，不是「模型输出」的改写——`docs/PROTOCOL.md` §7 记了这条边界。
- **层次不丢**，只去掉记号：`## 标题` 变成 `标题`，`- 项` 变成 `· 项`，围栏内的代码改为缩进两个空格。
- **答案只打印一遍**。原实现同时把流式增量和最终答案各打一次，同一个回答出现两遍（这正是「好像依然是没有总结段落」的一部分原因——两段文本把总结的位置占掉了）。
- **`answer` 段是唯一的收尾**。它不再只是「成功时才有的那段」，失败与中止也在这同一段里给出原因和建议（见 §4）。原先叠在它后面的「本轮总结」四行块已整体删除。
- **运行时默认只有三类过程行**：`step` / `tool` / `result`。工具参数按 `path` / `command` / `task` / `pattern` / `name` / `url` 取一个值摘要显示，截到 60 字符；取不到就退化为原文本压成一行。原始 JSON 不再出现在终端上。

---

## 附：断言清单（怎么知道上面这些不是摆拍）

演示靠肉眼，断言靠程序。八套断言在 `target/t/`（不入 git），累计 **666 条全绿**：

| 套件 | 条数 | 覆盖 |
|---|---|---|
| `P1T` | 30 | SSE 跨边界分片、工具参数多段拼接、语言槽位替换 |
| `P2T` | 27 | 工具注册与 Schema、路径越界拒绝、错误回文本不抛异常 |
| `P3T` | 32 | 并行区间真实重叠、树形连接符与字形、live 与非 live 的 escape 差异 |
| `P4T` | 61 | 报价与次高报价记账、成功率夹取、预算记账、闭环调节与熔断、写路径锁争用 |
| `P5T` | 120 | token 估算、协议件读写与上限淘汰、ROI 判定边界、产物名安全、压缩集成与续跑 |
| `P6T` | 79 | 双档配置来源与优先级、两档同源/异源、trailing slash、键对称与厂商名扫描 |
| `P7T` | 109 | 双厂商机制真实验证：报价、成功率漂移、控制器升降与熔断、降级链、对账聚合 |
| `R3T` | 208 | 标签中性与双语言键对称、`chat` 已移除、环境槽位替换无残留、`bash` 不再等待 stdin 且按原生编码解码、`runs.md` 落盘回灌、终端文本剥离、每次运行都能收尾记账、步数上限可配且 `0` 为不限、收尾总结块已退役且失败也走 `answer`、对账块由 `--scorecard` 控制（含子进程实跑的整屏对账） |

跑法：

```bash
java -cp "target/classes;target/t" P1T   # 其余同理
java -cp "target/classes;target/t" R3T
```
