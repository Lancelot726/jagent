# jagent

用 **Java 25** 写的终端 CLI coding agent（对标 Claude Code / Pi）。

差异化不在「换一门语言重写同类工具」，而在**把六条运行时机制做成可观察的工程实现**：模型路由、资源管理、ROI 判定、自适应并发控制、执行图、自动化闸门，**直接作用于运行时**，而不是写在文档里的口号。

---

## 目标

以**纯 JDK 零依赖**的 Java 实现，挑战 Go / Rust / C++ 写的同类工具，在**启动速度、产物体积、常驻内存**上进入同一量级竞争。

---

## 运行机制

六条机制都能在终端里看到，不是内部黑盒。演示脚本见 [`docs/DEMO.md`](docs/DEMO.md)。

| 机制 | 在程序里是什么 | 怎么看 |
|---|---|---|
| **模型路由** | 决定本轮用廉价档还是强力档 | 每轮打印 `pick cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)` |
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
