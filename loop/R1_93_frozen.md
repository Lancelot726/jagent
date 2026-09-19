# 冻结清单 · jagent

> 记录已拍板成为下一轮基线的文件及其校验值。`fin` 件从此只读。

## 冻结件（R1 定稿，2026-09-19 经作者 G4 拍板）

**基线 = git 提交**（本产品不进轮次命名体系，定稿以提交为凭，哈希即校验值）

| 项 | 值 |
|---|---|
| 仓库 | 本地 `jagent`（`git init -b main`），**未设远程** |
| 分支 | `main` |
| **交付基线提交** | `a8761b4` — `R1: runnable demo — pure-JDK zero-dep terminal agent` |
| 补充提交 | `2a1ca57` — `chore: ignore machine-local Claude Code settings and demo scratch` |
| 基线内容 | 上述两次提交的并集即为 R1 定稿内容（68 文件） |
| 署名 | Lancelot \<higerjoth1@foxmail.com\>（`-c` 临时传入，未写入 git 配置） |
| 入库文件数 | 68 |
| 排除 | `target/`、`work/`、`demo-run/`、`.claude/settings.local.json`、`*.class`、`*.jar` |

**交付物校验值（md5）**

| 文件 | md5 |
|---|---|
| `README.md` | `ca4100c3d64850e5338ea742c7d52500` |
| `docs/PROTOCOL.md` | `42507c70a429bb09883c7eb7a5cf8c07` |
| `docs/DEMO.md` | `a5984a75bee909773c105266bd17c5e4` |
| `loop/R1_30_design_lang_slots.wip.md` | `4f3638bbbf9d7aa839b2a3e803ca7b51` |
| `loop/R1_50_bench_startup.wip.md` | `47369a3f6ea1e77599ea13bb260b64b9` |

**密钥核查**：全库无 `sk-*` 形态字符串、无硬编码 `api_key=`；`.mvn/wrapper/` 仅含
`maven-wrapper.properties`（无 jar），故 `*.jar` 忽略规则不会打断 wrapper。

**基线只读声明**：交付基线为 `a8761b4`（`2a1ca57` 为其上的忽略规则修正），二者构成 R2 的起点。
R2 如需改动，一律新开提交，不改写这两个提交。

> 说明：`loop/90–99` 属滚动活文件，其定稿后的记录更新另行以 meta 提交推进，不影响上述交付基线。
> 因此不要用「分支 tip」当基线判据 —— 判据是本节列出的两个提交哈希。

## 本轮实际存在的文件

### loop/

| 文件 | 角色 |
|---|---|
| `R1_00_req_product.in.txt` | 需求原文，只读存档 |
| `R1_03_style_prompt.in.txt` | 系统提示词原稿，只读存档 |
| `R1_10_task_round.wip.md` | 本轮任务书 |
| `R1_30_design_lang_slots.wip.md` | 语言槽位机制设计 |
| `R1_90_req_constitution.md` | 需求宪法 |
| `R1_91_progress.md` | 进度图 |
| `R1_92_logs.txt` | 流水 |
| `R1_93_frozen.md` | 本文件 |
| `R1_94_rounds.md` | 轮次台账 |

### 产品（不进轮次命名体系，由 git 管版本）

**已存在**

`pom.xml`、`mvnw`、`mvnw.cmd`、`.mvn/`、`.gitignore`、
`src/main/java/jagent/**`（json / llm / tool / graph / agent / mem / term / core 八包）、
`src/main/resources/prompt/system.txt`（含 `{{user_lang}}` 槽位）、
`src/main/resources/lang/zh.properties`、`src/main/resources/lang/eng.properties`

**已建的交付物（S5 完成）**

| 文件 | 用途 | 状态 |
|---|---|---|
| `README.md` | 理念 + 怎么跑 + 元机制怎么看 | 已建 |
| `docs/PROTOCOL.md` | work 目录抽象文件名协议 | 已建 |
| `docs/DEMO.md` | 演示脚本：每条元机制怎么被观察到 | 已建 |
| `loop/R1_50_bench_startup.wip.md` | 启动耗时与体积基准 | 已建（wip） |
| `target/t/P5T.java` | P5 断言（不入 git；仅验证用） | 已建，120/120 |

**仍未做（全部属 R2）**

| 项 | 说明 |
|---|---|
| 发布到远程（GitHub） | G4 拍板为"只建本地仓库"，远程推送**明确不在本轮** |
| 压缩摘要逐轮累积修复 | 见轮次台账 R2 首批待办 |
| GraalVM native + RSS 基准 | 见 `R1_50_bench_startup.wip.md` §5 局限 |

G3 真终端实跑已于 2026-09-19 通过（原地刷新 / 字形整齐 / 对齐正常），详见 `R1_94_rounds.md`。

`work/`、`target/`、`demo-run/` 均为运行时目录，已 gitignore。
