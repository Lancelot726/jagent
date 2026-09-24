# jagent

用 **Java 25** 写的终端 CLI coding agent（对标 Claude Code / Pi）。

差异化不在「换一门语言重写同类工具」，而在**把六条运行时机制做成可观察的工程实现**：模型路由、资源管理、ROI 判定、自适应并发控制、执行图、自动化闸门，**直接作用于运行时**，而不是写在文档里的口号。

---

## 一句话主张

以**纯 JDK 零依赖**的 Java 实现，挑战 Go / Rust / C++ 写的同类工具，在**启动速度、产物体积、常驻内存**上进入同一量级竞争。

Java 常见的短板（启动慢、体积大）在客户端 agent 场景是硬指标，所以这不是宣传语，而是工程约束：零依赖是为了 native 编译与冷启动，不用 preview 特性是为了不锁死 JDK 版本、不拖累 native-image。

---

## 六条机制怎么作用到运行时

六条机制都能在终端里**肉眼看到**，不是内部黑盒。演示脚本见 [`docs/DEMO.md`](docs/DEMO.md)。

| 机制 | 在程序里是什么 | 怎么看 |
|---|---|---|
| **模型路由** | 密封报价决定本轮用廉价档还是强力档；采纳按次高报价结算 | 每轮打印 `pick cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)` |
| **资源管理** | token 预算记账 + 压缩的 ROI 判定（不是到阈值就压） | 打印 `compress 182 -> 16 tok ... ROI 2116.5x` 与预算行 |
| **ROI 判定** | 压缩决策算的是「剩余轮数 × 每轮省下的量」对比开销，`ROI > 1.5` 才动手 | 同上，四个量都打印出来，可当场复核 |
| **自适应并发控制** | 并发度是受控指标：错误率反馈调节 + 死区 + 熔断 + 抖动退避 | 健康时 `ctl:up 7->8`，故障时 `ctl:down 2->1` 且 `err=49%` |
| **执行图** | 所有任务画成执行图；多 agent 在同一张图上并行长子树 | 真终端底部实时 Unicode 树；`plan.md` 里有完整节点清单 |
| **自动化** | 调度即受控对象；人只在显式闸门处介入 | 无终端时 `ask_human` 自动放行；交互终端里则等待输入 |

---

## 快速开始

需要 **JDK 25**。构建用 Maven Wrapper，不需要预装 Maven。

```bash
./mvnw -q package
java -jar target/jagent.jar version
```

**离线演示**（无需任何密钥，MockClient 会自己跑出一条完整的 ReAct 轨迹）：

```bash
java -jar target/jagent.jar doctor
java -jar target/jagent.jar help
java -jar target/jagent.jar run fanout --cwd demo-run
```

每次 `run` 的**最后一屏就是 `answer`**：成功时是模型的收尾答复，失败或撞上步数上限时在同一位置说明停在哪一步、为什么：

```
  answer
完成：demo_a.txt 与 demo_b.txt 已写入并校验通过。

  [steps=4 nodes=26 in=1568 out=94 2648ms]
```

失败时 `answer` 段内改为一行动词化的原因加一条建议：

```
  answer
    本轮中止于第 1 步：all 2 attempts failed: ConnectException
    建议 可先看 work/plan.md 与 work/episodes.log 里的中间结果。
```

除了这一屏，运行过程只打印 `step` / `tool` / `result` 三类行；工具参数只摘要显示路径或命令本身，不打印原始 JSON。对账块（`summary` 那八行）**默认不打印**，需要时加 `--scorecard`。

每一次运行都会记进 `work/runs.md`，且下一次启动时被读回注入系统提示，所以**关掉重开之后模型仍然记得上一次做过什么**。

**交互式命令行**（不带参数启动即进入）：

```bash
java -jar target/jagent.jar
```

```
> 把 src 下所有 TODO 汇总
> :help
> exit
```

**接真模型**（OpenAI 兼容协议）：

```bash
java -jar target/jagent.jar run "把 src 下所有 TODO 汇总" \
  --provider openai --base-url https://<你的地址>/v1 --api-key <密钥> --model <模型名>
```

密钥也可以走环境变量 `JAGENT_API_KEY` / `JAGENT_BASE_URL` / `JAGENT_MODEL`，或写在 `work/jagent.properties`。**密钥绝不进代码、不进 git。**

常用选项：

| 选项 | 作用 |
|---|---|
| `--provider <mock\|openai>` | 模型通道，默认 `mock`（离线） |
| `--model-strong <name>` | 强力档模型，报价采纳时使用 |
| `--budget <tokens>` | token 预算上限，默认 40000 |
| `--max-steps <n>` | 单轮步数上限（根与子 agent 同用），默认 16；设 `0` 表示**不限步数**，退出只由模型自己收尾决定 |
| `--compress-after <tokens>` | 上下文超过该值才**评估**压缩，默认 6000 |
| `--resume` | 从 `work/plan.md` 的 `goal:` 续跑，可省略任务描述 |
| `--tool-delay <ms>` | 给工具加人为延迟，用于观察并行 |
| `--user-lang <zh\|eng>` | 面向用户的语言 |
| `--no-graph` | 关闭底部实时执行图（管道/CI 建议开启） |
| `--scorecard` | 在运行末额外汇总对账块（默认关闭，只给运维/调试看） |
| `--cwd <dir>` | 工作根目录 |

更多选项看 `java -jar target/jagent.jar help`。

---

## 语言分两层

- **L1 内部层**：固定英文 + 极强缩略。提示词、日志、`work/` 文件表头都在这层——内部追求最短 token，且缩略语法只在英文成立。
- **L2 面向层**：由用户首启选定（中文 / English），交付物与问答随之切换。

提示词只有**一份模板**，其中的语言位置写作槽位 `{{user_lang}}`，装配时替换。加一门语言 = 加一个 `resources/lang/<code>.properties` + 一枚枚举，**不动业务代码**。

同一份模板里还有四个**环境槽位** `{{os}}` / `{{shell}}` / `{{cwd}}` / `{{encoding}}`，装配时填入本机事实。这不是可选项：模型不知道自己在哪个 shell 上跑，就会写出 `date '+%Y-%m-%d'` 这种在 `cmd.exe` 上必然失败的写法（见 `docs/DEMO.md` §13）。

---

## 目录结构

```
src/main/java/jagent/
├── json/    零依赖 JSON 与 SSE 解析
├── llm/     流式客户端、MockClient、降级链、档位路由
├── tool/    工具注册表与内置工具（文件、bash、记忆、人类闸门）
├── graph/   执行图、节点状态、快照、准入与调度
├── agent/   ReAct 主循环、报价、预算、控制器、系统提示词装配
├── mem/     token 估算、work 目录协议、记忆、ROI 压缩
├── term/    终端编码、ANSI、语言包、执行图渲染、仪表盘、面向终端的文本剥离
└── core/    事件总线、本机环境探测（Env）、运行上下文（ScopedValue）

src/main/resources/
├── prompt/system.txt           含 {{user_lang}} 与四个环境槽位的模板
└── lang/{zh,eng}.properties    界面串语言包

docs/
├── PROTOCOL.md   work 目录抽象文件名协议与记忆的两条契约
└── DEMO.md       每条机制怎么被观察到
```

---

## 硬性约定

破了就是越界，写在这里供外部核对：

1. **纯 JDK 零依赖**，运行时不得引入任何第三方 jar。
2. **不使用任何 preview 特性**（不锁死 JDK、不拖累 native-image）。
3. 源码**不写注释**，实现取最小。
4. 密钥**不进代码、不进 git**。
5. 语言分两层（见上）。
6. **每阶段跑通再进下一阶段**。

---

## 验证

演示靠肉眼，断言靠程序。八套断言累计 **666 条全绿**：

| 套件 | 条数 | 覆盖 |
|---|---|---|
| `P1T` | 30 | SSE 跨边界分片、工具参数多段拼接、语言槽位替换 |
| `P2T` | 27 | 工具注册与 Schema、路径越界拒绝、错误回文本不抛异常 |
| `P3T` | 32 | 并行区间真实重叠、树形连接符与字形、live 与非 live 的 escape 差异 |
| `P4T` | 61 | 报价与次高报价记账、成功率夹取、预算记账、闭环调节与熔断、写路径锁争用 |
| `P5T` | 120 | token 估算、协议件读写与上限淘汰、ROI 判定边界、产物名安全、压缩集成与续跑 |
| `P6T` | 79 | 双档配置来源与优先级、两档同源/异源、trailing slash、键对称与厂商名扫描 |
| `P7T` | 109 | 双厂商机制真实验证：报价、成功率漂移、控制器升降与熔断、降级链、对账聚合 |
| `R3T` | 208 | 标签中性与双语言键对称、`chat` 已移除、环境槽位替换无残留、`bash` 不再等待 stdin 且按原生编码解码、`runs.md` 落盘回灌、终端文本剥离、每次运行都能收尾记账、步数上限可配且 `0` 为不限、收尾总结块已退役且失败也走 `answer`、对账块由 `--scorecard` 控制 |

```bash
java -cp "target/classes;target/t" P1T    # 其余同理
java -cp "target/classes;target/t" R3T
```

断言源在 `target/t/`，**不入 git**（同 `work/`、`demo-run/` 的理由：本机验证产物）。

---

## 当前状态

**R1 完成**：可运行 demo 已落地，上述六条机制全部可观察。

**R2 完成**：双厂商接入（廉价档 / 强力档各自独立地址与密钥），四组机制在两档之间真实验证过，对账单汇总全部机制。

**R3 完成**：终端交互与显示口径。除「不带参数启动进交互式命令行、执行图只画结构与状态、机制标签中性化」外，另并入现场反馈的四项：把本机环境事实（os / shell / cwd / console encoding）注入系统提示；`bash` 工具关闭子进程 stdin 并按控制台编码解码，使 `cmd.exe` 的内建命令不再挂死、中文不再乱码；`runs.md` 让「本地记忆」跨进程成立，同一会话内的上下文也不再丢；终端只显示剥掉 Markdown 记号的纯文本，且每次运行——无论成败——都在同一段 `answer` 里收尾（原先另起的「本轮总结」块与对账块已删掉，后者改为 `--scorecard` 按需索取）。步数上限由写死的 16 变成配置项 `--max-steps`（根与子 agent 同用），设 `0` 即不限步数。

**R4 本轮**：术语口径整理。把散在 `loop/` 过程文件与 `README` / `docs/` 里的跨学科借词统一换成计算机工程与 agent 领域的通用说法（模型路由 / 预算记账 / ROI 判定 / 自适应并发控制 / 熔断 / 执行图），使文档读起来像一套成熟工程系统的架构说明，而不是跨学科借词。**只改措辞，不动机制、不改代码行为**；只读输入原稿 `loop/*.in.txt` 按「只增不改」铁律保持逐字原样，新旧用语的对应关系另建映射表（见 `loop/R4_10_task_round.wip.md`）。

**R5 待触发**：GraalVM native 构建（`pom.xml` 已预留 `native` profile）、分层流程图布局、终端 raw mode 行编辑（方向键历史等）、熔断与中止策略的关系、子 agent 工作区隔离、压缩摘要自引用。自适应演化项暂缓，无期限。

三档目标：简单档是「demo 跑通、体现 agent 理解」，普通档是「基本完成全部要点」，最高档是「现象级产品，实现性能对标」。
