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

## 3 流式对话：先看到字形一个个落下来

```bash
java -jar target/jagent.jar chat "你好"
```

**看什么**：文本逐字流出，末尾打印本轮的 token 与耗时。

```
[gpt-4o-mini]
你好
  [stop in=... out=... tools=0 ...ms]
```

**观察点**：SSE 分片会**跨读边界**切在任意字节处（P1T 用 7 字节一片刻意制造这种切法），解析器靠增量缓冲拼接，不靠「一包一事件」的侥幸。

---

## 4 自主循环：模型自己决定调工具、看结果、再决定

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph
```

**看什么**：每轮打印「轮次 / 工具 / 结果」，直到模型自己收尾。

```
  轮次 1
并行派发两个子 agent。  工具 spawn_agent {"task":"sub_write_alpha","label":"alpha"}
  工具 spawn_agent {"task":"sub_write_beta","label":"beta"}
  结果 完成：...
  轮次 2
并行写入两个文件。  工具 write_file {"path":"demo/fanout_a.txt",...}
```

**观察点**：没有人在循环里控制流程。ReAct 的「想—做—看」由模型驱动，程序只提供工具与约束。任务完成后模型主动停止，末行给出 `[步数=4 nodes=29 in=... out=...]` 这类可核算的统计。

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

## 7 博弈论：用密封拍卖决定用哪个模型档位

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph
```

**看什么**：每轮模型调用前，打印一行竞价结果：

```
  竞价 cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)
```

**观察点**：

- 两个档位（廉价 / 强力）各自**密封报价**，报价 = 估值 × 成功率 − 成本，互不可见。
- 中标者按**第二高价**结算（Vickrey），所以 `pay` 等于落败者的报价（0.44），而不是自己的（0.51）。
- `+0.14` 是本次成交给系统带来的**利润**，进预算账本。
- 估值里含两个信号：控制论给出的健康度 `valueScale`，以及预算压力 `usedFraction`——预算越紧，出价越低，越倾向廉价档。**这就是博弈论、控制论、经济学三者在同一处交汇。**

---

## 8 经济学：预算账本与压缩 ROI

```bash
java -jar target/jagent.jar run fanout --cwd demo-run --no-graph --compress-after 64
```

> 注：默认 `--compress-after` 是 6000；mock 短任务上下文达不到，所以演示时压到 64 好让压缩**当场发生**。

**看什么**：预算行 + 压缩行。

```
  压缩上下文 182 -> 16 tok  ctx 182 > 64, 204 rounds x 166 saved vs 16 cost -> ROI 2116.5x
  [预算 2031/20000 tok 利润 1.31 conc=8 err=0% ctl:up 7->8]
```

**观察点**：

- **预算账本**：输入、输出 token 都记账，剩余额度决定还能不能继续；`利润` 来自上面竞价成交的累计。
- **压缩不按阈值触发，按 ROI 决策**：到阈值只是「可以评估」，真正动手要算——
  `roundsLeft = 剩余预算 / 每轮消耗`，`benefit = 剩余轮数 × 每轮省下的 token`，`ROI = benefit / cost`，只有 `ROI > 1.5` 才压。
- 所以阈值只是门票，**账才算是否进场**。打印里把四个量都摊开了，可以当场复核。

---

## 9 控制论：并发度自己升降，出错就跳闸

先看健康时的**闭环升并发**（上面第 8 节末行 `ctl:up 7->8`）：连续成功 → 并发逐级上调。

再看故障时的**熔断**——把 base_url 指向一个死端口：

```bash
java -jar target/jagent.jar run breaker --cwd demo-breaker \
  --provider openai --base-url http://127.0.0.1:9/v1 --api-key k --model m --no-graph
```

```
  竞价 cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)
  模型调用失败，已中止本轮: all 2 attempts failed: ConnectException
  [步数=1 nodes=2 in=0 out=0 164ms]
  [预算 0/5000 tok 利润 0.14 conc=1 err=49% ctl:down 2->1]
```

**看什么**：`err=49%`、`conc=1`、`ctl:down 2->1`。

**观察点**：

- **并发不是常量**，是控制回路里的**被控量**：错误率做积分调节，死区防止抖动，连续失败触发跳闸，退避重试带抖动。
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
# demo-run/work/plan.md  facts.md  summary.md  episodes.log  artifacts/
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

**观察点**：文件名是**抽象名系统**（`plan` / `facts` / `summary` / `episodes` / `artifacts`），与任务、时间无关，规则见 `docs/PROTOCOL.md`。压缩发生后 `summary.md` 会留下摘要，`episodes.log` 留下只追加的流水——**上下文被折叠了，事实仍在磁盘上**。

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

## 附：断言清单（怎么知道上面这些不是摆拍）

演示靠肉眼，断言靠程序。五套断言在 `target/t/`（不入 git），累计 **266 条全绿**：

| 套件 | 条数 | 覆盖 |
|---|---|---|
| `P1T` | 26 | SSE 跨边界分片、工具参数多段拼接、语言槽位替换 |
| `P2T` | 27 | 工具注册与 Schema、路径越界拒绝、错误回文本不抛异常 |
| `P3T` | 32 | 并行区间真实重叠、树形连接符与字形、live 与非 live 的 escape 差异 |
| `P4T` | 61 | 竞价与 Vickrey 记账、声誉夹取、预算账本、控制律与跳闸、写路径锁争用 |
| `P5T` | 120 | token 估算、协议件读写与上限淘汰、ROI 判定边界、产物名安全、压缩集成与续跑 |

跑法：

```bash
java -cp "target/classes;target/t" P1T   # 四套同理
java -cp "target/classes;target/t" P5T
```
