# jagent

用 **Java 25** 写的终端 CLI coding agent（对标 Claude Code / Pi）。

差异化不在「换一门语言重写同类工具」，而在**方法论**：博弈论、进化论、经济学、工程经济学、图工程、自动化与控制论，**直接作用于运行时**，而不是写在文档里的口号。

---

## 一句话主张

以**纯 JDK 零依赖**的 Java 实现，挑战 Go / Rust / C++ 写的同类工具，在**启动速度、产物体积、常驻内存**上进入同一量级竞争。

Java 常见的短板（启动慢、体积大）在客户端 agent 场景是硬指标，所以这不是宣传语，而是工程约束：零依赖是为了 native 编译与冷启动，不用 preview 特性是为了不锁死 JDK 版本、不拖累 native-image。

---

## 方法论怎么作用到运行时

六条机制都能在终端里**肉眼看到**，不是内部黑盒。演示脚本见 [`docs/DEMO.md`](docs/DEMO.md)。

| 机制 | 在程序里是什么 | 怎么看 |
|---|---|---|
| **博弈论** | 密封拍卖决定本轮用廉价档还是强力档；中标按第二高价（Vickrey）结算 | 每轮打印 `竞价 cheap=0.44 strong=0.51 -> strong (pay 0.44, +0.14)` |
| **经济学** | token 预算账本 + 压缩的 ROI 判定（不是到阈值就压） | 打印 `压缩上下文 182 -> 16 tok ... ROI 2116.5x` 与预算行 |
| **工程经济学** | 压缩决策算的是「剩余轮数 × 每轮省下的量」对比成本，`ROI > 1.5` 才动手 | 同上，四个量都打印出来，可当场复核 |
| **控制论** | 并发度是被控量：错误率积分调节 + 死区 + 跳闸 + 抖动退避 | 健康时 `ctl:up 7->8`，故障时 `ctl:down 2->1` 且 `err=49%` |
| **图工程** | 所有任务画成执行图；多 agent 在同一张图上并行长子树 | 真终端底部实时 Unicode 树；`plan.md` 里有完整节点清单 |
| **自动化** | 调度即被控对象；人只在显式闸门处介入 | 无终端时 `ask_human` 自动放行；交互终端里则等待输入 |

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
java -jar target/jagent.jar chat "你好"
java -jar target/jagent.jar run fanout --cwd demo-run
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
| `--model-strong <name>` | 强力档模型，竞价中标时使用 |
| `--budget <tokens>` | token 预算上限，默认 40000 |
| `--compress-after <tokens>` | 上下文超过该值才**评估**压缩，默认 6000 |
| `--resume` | 从 `work/plan.md` 的 `goal:` 续跑，可省略任务描述 |
| `--tool-delay <ms>` | 给工具加人为延迟，用于观察并行 |
| `--user-lang <zh\|eng>` | 面向用户的语言 |
| `--no-graph` | 关闭底部实时执行图（管道/CI 建议开启） |
| `--cwd <dir>` | 工作根目录 |

更多选项看 `java -jar target/jagent.jar help`。

---

## 语言分两层

- **L1 内部层**：固定英文 + 极强缩略。提示词、日志、`work/` 文件表头都在这层——内部追求最短 token，且缩略语法只在英文成立。
- **L2 面向层**：由用户首启选定（中文 / English），交付物与问答随之切换。

提示词只有**一份模板**，其中的语言位置写作槽位 `{{user_lang}}`，装配时替换。加一门语言 = 加一个 `resources/lang/<code>.properties` + 一枚枚举，**不动业务代码**。

---

## 目录结构

```
src/main/java/jagent/
├── json/    零依赖 JSON 与 SSE 解析
├── llm/     流式客户端、MockClient、降级链、档位路由
├── tool/    工具注册表与内置工具（文件、bash、记忆、人类闸门）
├── graph/   执行图、节点状态、快照、准入与调度
├── agent/   ReAct 主循环、竞价、预算、控制器、系统提示词装配
├── mem/     token 估算、work 目录协议、记忆、ROI 压缩
├── term/    终端编码、ANSI、语言包、执行图渲染、仪表盘
└── core/    事件总线、运行上下文（ScopedValue）

src/main/resources/
├── prompt/system.txt           含 {{user_lang}} 槽位的模板
└── lang/{zh,eng}.properties    界面串语言包

docs/
├── PROTOCOL.md   work 目录抽象文件名协议
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

演示靠肉眼，断言靠程序。五套断言累计 **266 条全绿**：

| 套件 | 条数 | 覆盖 |
|---|---|---|
| `P1T` | 26 | SSE 跨边界分片、工具参数多段拼接、语言槽位替换 |
| `P2T` | 27 | 工具注册与 Schema、路径越界拒绝、错误回文本不抛异常 |
| `P3T` | 32 | 并行区间真实重叠、树形连接符与字形、live 与非 live 的 escape 差异 |
| `P4T` | 61 | 竞价与 Vickrey 记账、声誉夹取、预算账本、控制律与跳闸、写路径锁争用 |
| `P5T` | 120 | token 估算、协议件读写与上限淘汰、ROI 判定边界、产物名安全、压缩集成与续跑 |

```bash
java -cp "target/classes;target/t" P1T    # 四套同理
java -cp "target/classes;target/t" P5T
```

---

## 当前状态

**R1 完成**：可运行 demo 已落地，上述六条机制全部可观察。

**R2 计划**：进化论基因组与变异算子、GraalVM native 构建（`pom.xml` 已预留 `native` profile）、分层流程图布局、终端 raw mode 行编辑。

三档目标：简单档是「demo 跑通、体现 agent 理解」，普通档是「基本完成全部要点」，最高档是「现象级产品，实现军备竞赛」。
