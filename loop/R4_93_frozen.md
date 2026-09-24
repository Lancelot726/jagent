# 冻结清单 · jagent

> 记录已拍板成为下一轮基线的文件及其校验值。`fin` 件从此只读。
> 本文件只记 **R4**。R1 / R2 / R3 的冻结原文见各自轮的 `R1_93_frozen.md` / `R2_93_frozen.md` / `R3_93_frozen.md`。

## 冻结件（R4 定稿，2026-09-24 经作者 G4 拍板）

**基线 = git 提交**（本产品不进轮次命名体系，定稿以提交为凭，哈希即校验值）

| 项 | 值 |
|---|---|
| 仓库 | 本地 `jagent`，**未设远程**（沿用 R1 决定） |
| 分支 | `main` |
| **R1 基线（只读）** | `a8761b4` — `R1: runnable demo — pure-JDK zero-dep terminal agent`（+ 修正 `2a1ca57`） |
| **R2 基线（只读）** | `996d71d` — `R2: dual-vendor tiers + mechanism verification on real models` |
| **R3 基线（R4 的起点，只读）** | `630f92b` — `R3: terminal UX — REPL, neutral labels, single answer tail` |
| **R4 交付基线提交** | `2917c0c` — `R4: docs — drop cross-discipline vocabulary for plain engineering terms` |
| 署名 | Lancelot \<higerjoth1@foxmail.com\>（`-c` 临时传入，未写入 git 配置） |
| 排除 | `target/`、`work/`、`demo-run/`、`.claude/settings.local.json`、`*.class`、`*.jar` |

**交付物校验值（md5）**

产品侧（本轮只改语言包，**零 Java 源码改动**）

| 文件 | md5 | 角色 |
|---|---|---|
| `src/main/resources/lang/zh.properties` | `e656aa7e275a5c67e560883eccf00201` | `opt.modelstrong` / `opt.scorecard` / `tier.sameSrc` 三处用户文案去学科词 |
| `src/main/resources/lang/eng.properties` | `fbfdea4c347a3bf9f19db502c81a3302` | 同上（zh / eng 键对称，各 94 键） |

文档

| 文件 | md5 | 角色 |
|---|---|---|
| `README.md` | `bbd6bcced8869057a7f15e329f9fba7a` | §1 项目定义改写、机制表与选项去学科词、状态节改 R4/R5 口径 |
| `docs/PROTOCOL.md` | `625373b310b2a85bc70b9eed521ff979` | §7 去学科词（出价→报价） |
| `docs/DEMO.md` | `53f98f8c2a8a6057f4469f9a8b7c46f4` | §7–§9 标题与观察点去学科词 |

过程记录（`loop/`，随元层提交入库，见下）

R1–R3 各轮的 `R*_10` / `R*_90` / `R*_91` / `R*_92` / `R*_93` / `R*_94` / `R2_95` 的措辞已按
`R4_10` §3 映射表统一。**未动的有三类**：只读原稿 `*.in.txt`、逐字证据 `R2_50` / `R2_51`、
以及 R4 记录本身（须引用旧词）。

**断言基线**：P1T 30 / P2T 27 / P3T 32 / P4T 61 / P5T 120 / P6T 79 / P7T 109 / R3T 208
= **666 全绿**。本轮未改任何 Java 源码；因语言包是断言的数据源，改后已同步进 `target/classes` 并复跑确认。

**密钥核查**：产品侧（`src/` / `README.md` / `docs/`）无 `sk-*` 形态字符串、无硬编码 `api-key=sk`、无厂商地址。

**基线只读声明**：R4 交付基线为 `2917c0c`。R5 如需改动，一律新开提交，不改写该提交。

> 说明：`loop/90–99` 属滚动活文件，其定稿后的记录更新另行以 meta 提交推进，不影响上述交付基线。
> 因此不要用「分支 tip」当基线判据 —— 判据是本节列出的提交哈希。

---

## R4 明确不取、转 R5 的项（已知缺口，不是未知风险）

| # | 项 | 现状 |
|---|---|---|
| 1 | 完整版 raw mode 行编辑（方向键历史 / 行内编辑 / Tab 补全） | R3 只取了"有输入循环"这一半，见 `R3_90` 修订 2 |
| 2 | 熔断与中止策略的关系；**步数不限时的第二道硬闸** | `max-steps` 是当前唯一循环出口，`Budget` 只记账、`Governor.open` 未被读取，见 `R3_90` 修订 8 |
| 3 | 子 agent 工作区隔离 | 未动 |
| 4 | 压缩摘要不得自引用累积 | 未动 |
| 5 | GraalVM native 构建 + 启动 / 体积 / **常驻 RSS** 基准 | 未动 |
| 6 | 分层流程图布局 | 未动 |

**暂缓（无期限）**：自适应演化参数集与变异操作 —— 作者 2026-09-20「暂时删掉」，见 `R3_90` 修订 4。

## 本轮实际存在的文件

### loop/

| 文件 | 角色 |
|---|---|
| `R4_00_req_product.in.txt` | 本轮触发原话 + G1 四问四答，只读存档 |
| `R4_10_task_round.wip.md` | 本轮任务书（含 §3 术语映射表 29 条 + 修辞修正表） |
| `R4_90_req_constitution.md` | 需求宪法修订页（2 条修订） |
| `R4_91_progress.md` | 进度图 |
| `R4_92_logs.txt` | 流水 |
| `R4_93_frozen.md` | 本文件 |
| `R4_94_rounds.md` | 轮次台账 |

`30`–`60` 槽位本轮**未建**：本轮无独立素材 / 分析 / 草稿 / 成品文件（产出即文档与语言包）。

### 产品（不进轮次命名体系，由 git 管版本）

R4 未新增顶层交付物，也无新源码；改动全部落在既有文档与两个语言资源上，清单见上方 md5 表。

`work/`、`target/`、`demo-run/` 均为运行时目录，已 gitignore。
