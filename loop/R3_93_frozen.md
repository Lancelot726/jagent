# 冻结清单 · jagent

> 记录已拍板成为下一轮基线的文件及其校验值。`fin` 件从此只读。
> 本文件只记 **R3**。R1 / R2 的冻结原文见各自轮的 `R1_93_frozen.md` / `R2_93_frozen.md`。

## 冻结件（R3 定稿，2026-09-20 经作者 G4 拍板）

**基线 = git 提交**（本产品不进轮次命名体系，定稿以提交为凭，哈希即校验值）

| 项 | 值 |
|---|---|
| 仓库 | 本地 `jagent`，**未设远程**（沿用 R1 决定） |
| 分支 | `main` |
| **R1 基线（只读）** | `a8761b4` — `R1: runnable demo — pure-JDK zero-dep terminal agent`（+ 修正 `2a1ca57`） |
| **R2 基线（R3 的起点，只读）** | `996d71d` — `R2: dual-vendor tiers + mechanism verification on real models` |
| **R3 交付基线提交** | `630f92b` — `R3: terminal UX — REPL, neutral labels, single answer tail` |
| 署名 | Lancelot \<higerjoth1@foxmail.com\>（`-c` 临时传入，未写入 git 配置） |
| 排除 | `target/`、`work/`、`demo-run/`、`.claude/settings.local.json`、`*.class`、`*.jar` |
| 显式排除（**非本项目**） | `survey/` 及根目录 `fetch_arxiv.py` / `ml_survey_compare.py` / `probe_arxiv.py` / `task_arxiv_raw.json` —— 与本轮无关的未跟踪文件，**未纳入任何提交** |

**交付物校验值（md5）**

产品侧（R3 新增/修改）

| 文件 | md5 | 角色 |
|---|---|---|
| `src/main/java/jagent/Cli.java` | `e47811b6e93be3e5ef42d7b4c1931f94` | REPL 读行循环 + 收尾只留 `answer` + `--scorecard` 开关 |
| `src/main/java/jagent/Config.java` | `ff9e00ade92dafdba7082342a501f739` | `maxSteps`（`intAny`）+ `scorecard` 两个新键 |
| `src/main/java/jagent/core/Env.java` | `2a30dac1f998ec2a6737518b0229a56e` | **新增**：本机环境事实（os / shell / cwd / encoding） |
| `src/main/java/jagent/term/Text.java` | `bbfd1db96598d1567e10cf0fca7498d1` | **新增**：显示层剥 Markdown 记号 |
| `src/main/java/jagent/agent/Agent.java` | `b17699ccc3eebf79a82a7264aec2d8e9` | 收尾节点 note 改固定标记 `answer`；`summarize()` 删除；会话内上下文 |
| `src/main/java/jagent/agent/SpawnTool.java` | `feb32c150207392c983a3f70c07ecb44` | `create` 增 `maxSteps` 参数并与根共用 |
| `src/main/java/jagent/mem/Memory.java` | `ac15e6614eb28a315b5910c5b65326bf` | `recordRun` 落盘 + 回灌 |
| `src/main/java/jagent/mem/WorkDir.java` | `8338a0c3501451775a74f2ce9e02ab2c` | `runs.md` 路径 |
| `src/main/java/jagent/term/GraphView.java` | `7bc0496fbb707c12ff16ad29223028f7` | `clip` 48→60，消除二次截断 |
| `src/main/java/jagent/term/Term.java` | `60048b271145371fdac5017d82cfc0d1` | 终端 TTY 判定与重绘 |
| `src/main/java/jagent/tool/BashTool.java` | `0a4d8128897146e2e84dad50160495ed` | 子进程 stdin 关闭 + 按控制台原生编码解码 |
| `src/main/resources/prompt/system.txt` | `224bcb6135cb1918ad861b2c3a31748c` | 注入环境事实槽位 |
| `src/main/resources/lang/zh.properties` | `dbe79561747d123b4cca872b937f589a` | 删 `rep.*` 十七键与 `run.abort`；增 `run.failed` / `run.hint*` / `opt.scorecard` |
| `src/main/resources/lang/eng.properties` | `7beafa6db6301a7764e790967d4129b4` | 同上（L1 标签两档同值） |

文档（显示层改动必须同步，见 `R3_10` §2）

| 文件 | md5 | 角色 |
|---|---|---|
| `README.md` | `95e9f0b2ef8ad76e0927142f8ed25582` | 收尾示例重写、`--scorecard` / `--max-steps` 选项行、验证数 666 |
| `docs/PROTOCOL.md` | `030ae38e223185ec75c69ccc66ce050b` | §7 增「收尾唯一落点 / 自证默认静默 / 记账与呈现分离」三条 |
| `docs/DEMO.md` | `a0eac2debca0e67cab0383e3f11aac5f` | §4 换真实整屏样例（含失败）、§13–§15 追加四项落点 |

断言（`target/t/`，**不入 git**，仅验证用）

| 文件 | md5 | 条数 |
|---|---|---|
| `target/t/R3T.java` | `daa121be44e71f0c70a09b714bf66e74` | 208（本轮新建） |
| `target/t/P3T.java` | `87c66206f4ec290ae90f42ee65e71117` | 32（4 处随标签同步） |
| `target/t/P4T.java` | `66c1d3a77c2560b2643f9f2b9eecfdf5` | 61（`SpawnTool.create` 两处调用补 `maxSteps`） |
| `target/t/P7T.java` | `b95dff409e334f6c5b9341f3c4279e98` | 109（4 节随标签同步） |

**断言基线**：P1T 30 / P2T 27 / P3T 32 / P4T 61 / P5T 120 / P6T 79 / P7T 109 = 458（R2 基线）
＋ R3T 208 = **666 全绿**（冻结前复跑一次确认）。

**密钥核查**：产品侧（`src/` / `README.md` / `docs/`）无 `sk-*` 形态字符串、无硬编码 `api-key=sk`、
无厂商地址。`loop/` 内的命中仍只有规则表述本身（`R1_90` §3.5、`R2_92` / `R2_94` 记录「默认 base-url 由
厂商地址改为空」这一改动、`R2_93` 的核查语句）。**`survey/` 目录内有厂商地址命中，但该目录非本项目、未纳入提交**。

**基线只读声明**：R3 交付基线为 `630f92b`。R4 如需改动，一律新开提交，不改写该提交。

> 说明：`loop/90–99` 属滚动活文件，其定稿后的记录更新另行以 meta 提交推进，不影响上述交付基线。
> 因此不要用「分支 tip」当基线判据 —— 判据是本节列出的提交哈希。

---

## R3 明确不取、转 R4 的项（已知缺口，不是未知风险）

| # | 项 | 现状 |
|---|---|---|
| 1 | 完整版 raw mode 行编辑（方向键历史 / 行内编辑 / Tab 补全） | R3 只取了"有输入循环"这一半，见 `R3_90` 修订 2 |
| 2 | 熔断与中止策略的关系；**步数不限时的第二道硬闸** | `max-steps` 是当前唯一循环出口，`Budget` 只记账、`Governor.open` 未被读取，见 `R3_90` 修订 8 |
| 3 | 子 agent 工作区隔离（R2 的 d3 中两子 agent 互相读到对方产物） | 未动 |
| 4 | 压缩摘要不得自引用累积 | 未动 |
| 5 | GraalVM native 构建 + 启动/体积/**常驻 RSS** 基准 | 未动，本轮未引入新依赖，native 可行性未变化 |
| 6 | 分层流程图布局 | 未动 |
| 7 | 瞬时错误（429 等）仍**中止整轮** | 作者未要求改，本轮只在 `answer` 段里说明卡点（见 `R3_10` §2.2 承接） |

**暂缓（无期限）**：进化论基因组与变异算子 —— 作者 2026-09-20「暂时删掉」，见 `R3_90` 修订 4。

## 本轮实际存在的文件

### loop/

| 文件 | 角色 |
|---|---|
| `R3_00_req_product.in.txt` | 首轮需求原文，只读存档 |
| `R3_04_req_field.in.txt` | G3 第一回现场实录（作者投的终端 209 行），只读存档 |
| `R3_05_req_field_report.in.txt` | G3 第二回现场意见（逐字 + 三条归类 + 作者裁定），只读存档 |
| `R3_10_task_round.wip.md` | 本轮任务书（含 §2.1–§2.4 四次口径追加） |
| `R3_90_req_constitution.md` | 需求宪法修订页（8 条修订） |
| `R3_91_progress.md` | 进度图 |
| `R3_92_logs.txt` | 流水 |
| `R3_93_frozen.md` | 本文件 |
| `R3_94_rounds.md` | 轮次台账 |

`30`–`60` 槽位本轮**未建**：本轮无独立素材/分析/草稿/成品文件（产出即源码与文档）。

### 产品（不进轮次命名体系，由 git 管版本）

R3 未新增顶层交付物（无新 README/docs 文件）；源码新增两个类（`core/Env.java`、`term/Text.java`），
其余改动落在既有源码、语言资源与既有三份文档上，清单见上方 md5 表。

`work/`、`target/`、`demo-run/` 均为运行时目录，已 gitignore。
