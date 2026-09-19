# 冻结清单 · jagent

> 记录已拍板成为下一轮基线的文件及其校验值。`fin` 件从此只读。

## 冻结件（R2 定稿，2026-09-20 经作者 G4 拍板）

**基线 = git 提交**（本产品不进轮次命名体系，定稿以提交为凭，哈希即校验值）

| 项 | 值 |
|---|---|
| 仓库 | 本地 `jagent`，**未设远程**（沿用 R1 决定） |
| 分支 | `main` |
| **R1 基线（R2 的起点，只读）** | `a8761b4` — `R1: runnable demo — pure-JDK zero-dep terminal agent`（+ 修正 `2a1ca57`） |
| **R2 交付基线提交** | `996d71d` — `R2: dual-vendor tiers + mechanism verification on real models` |
| 署名 | Lancelot \<higerjoth1@foxmail.com\>（`-c` 临时传入，未写入 git 配置） |
| 排除 | `target/`、`work/`、`demo-run/`、`.claude/settings.local.json`、`*.class`、`*.jar` |

**交付物校验值（md5）**

产品侧（R2 新增/修改）

| 文件 | md5 | 角色 |
|---|---|---|
| `src/main/java/jagent/Config.java` | `f4b14947b474635a921dedb50a89f485` | 双档位独立配置 |
| `src/main/java/jagent/Cli.java` | `11a68cfbf3211a765251befe0538aea7` | 档位化客户端工厂 + 对账块打印 |
| `src/main/java/jagent/agent/Scorecard.java` | `b61c61e35bc4987e46e8912034429c3c` | 机制对账单 |
| `src/main/java/jagent/agent/Auction.java` | `654e42bc1829636984ef1309f51a7f43` | 中标计数 |
| `src/main/java/jagent/agent/Governor.java` | `7060c09547c2b8be53a629b5e719985d` | 上下调与熔断计数 |
| `src/main/java/jagent/graph/Admission.java` | `d6824a03d3373d96f0d2f1fea8e1f3f2` | 峰值计数 + 降额非阻塞回收 |
| `src/main/java/jagent/graph/Scheduler.java` | `1ce080d19d3bc04d916b8c8fa6eef177` | 嵌套许可修复 |
| `src/main/java/jagent/llm/OpenAiClient.java` | `9231d215b1a7ae63f5104aaf4b39bd89` | 推理内容捕获 |
| `src/main/java/jagent/llm/Turn.java` | `aaaf01e1f45a448193a0cd391c64e3cb` | 推理分量 |
| `src/main/java/jagent/llm/Message.java` | `792a108897ab937aa7f376ca01e9f3bd` | 推理内容回传 |
| `src/main/java/jagent/llm/Delta.java` | `9d8b3c7d20e9cb93aed0e90a98f272e0` | 推理增量事件 |
| `src/main/java/jagent/llm/FallbackClient.java` | `d37f4f4750cf2373497657b7813848be` | 降级与重试计数 |
| `src/main/java/jagent/llm/MockClient.java` | `451eb8949de327ca94e3cf5b1924e940` | 接口对齐 |
| `src/main/java/jagent/agent/Agent.java` | `29a5af99d22aaf2284dc78d92fb7c5af` | 推理内容入会话 |
| `src/main/java/jagent/mem/WorkDir.java` | `a3e4a3a9cbe5f7b6b196f7cfe80718b4` | 对账单落盘路径 |
| `src/main/resources/lang/zh.properties` | `e27903f1294a5656e7ee56d2707e3dbd` | 中档界面串 |
| `src/main/resources/lang/eng.properties` | `9c5934037695aa8461567a901dc10240` | 英档界面串 |

证据与验证（`loop/`）

| 文件 | md5 |
|---|---|
| `loop/R2_50_mech_evidence.wip.txt` | `8b1bfe7223a63b44262c49f107f31627` |
| `loop/R2_51_real_vendor_evidence.wip.txt` | `4486a78bd7a83aa6e68dc6682ada26a4` |
| `loop/R2_95_mech_battery.sh` | `f5fbf85a77f07484c32b46353fb6b1a2` |

断言（`target/t/`，不入 git，仅验证用）

| 文件 | md5 | 条数 |
|---|---|---|
| `target/t/P1T.java` | `9d83be65d2f64db7e9c43b3574b154d0` | 30 |
| `target/t/P7T.java` | `bfbfedfe02b306da1924051a4ed62c25` | 109 |

**断言基线**：P1T 30 / P2T 27 / P3T 32 / P4T 61 / P5T 120 / P6T 79 / P7T 109 = **458 全绿**。

**密钥核查**：全库无 `sk-*` 形态字符串、无硬编码 `api-key=sk`；`loop/` 内无厂商名/地址/密钥
（唯一命中"厂商名"的是 `R2_90` 修订 3 的规则表述本身——它规定这些名字不得出现）。

**基线只读声明**：R2 交付基线为 `996d71d`。R3 如需改动，一律新开提交，不改写该提交。

> 说明：`loop/90–99` 属滚动活文件，其定稿后的记录更新另行以 meta 提交推进，不影响上述交付基线。
> 因此不要用「分支 tip」当基线判据 —— 判据是本节列出的提交哈希。

---

## R2 送审带回、**未并入本轮**的意见（已转 R3）

作者在 G3 复核时提出、经 G4 裁定不并入 R2 的四项：

| # | 意见 | 影响面 |
|---|---|---|
| 1 | 执行图节点摘要显示不全（二次截断叠加） | 显示 |
| 2 | `chat` 推理与答案黏在同一行 | 显示 |
| 3 | 无 REPL 输入界面（R1 推迟的 raw mode 行编辑） | 交互（新功能） |
| 4 | 机制标签带学科色彩，宜中性化 | 措辞口径 |

**这四项是 R2 基线的已知缺陷，不是未知风险** —— R3 先修它们。

## 本轮实际存在的文件

### loop/

| 文件 | 角色 |
|---|---|
| `R2_00_req_product.in.txt` | 需求原文，只读存档 |
| `R2_10_task_round.wip.md` | 本轮任务书 |
| `R2_50_mech_evidence.wip.txt` | mock 电池证据（A–E 五场景） |
| `R2_51_real_vendor_evidence.wip.txt` | 真实双厂商证据（d1–d5） |
| `R2_90_req_constitution.md` | 需求宪法修订页（5 条修订） |
| `R2_91_progress.md` | 进度图 |
| `R2_92_logs.txt` | 流水 |
| `R2_93_frozen.md` | 本文件 |
| `R2_94_rounds.md` | 轮次台账 |
| `R2_95_mech_battery.sh` | mock 电池脚本 |

### 产品（不进轮次命名体系，由 git 管版本）

R2 未新增顶层交付物（无新 README/docs 文件）；改动全部落在既有源码与语言资源上，
清单见上方 md5 表。`R1` 的 `README.md` / `docs/PROTOCOL.md` / `docs/DEMO.md` 未改。

已删除：`CLAUDE.md`（作者 2026-09-19 删除；其语言条款硬编码 Zh，与硬约定 #5 冲突，
删除即消除冲突，见 `R2_90` 修订 4）。

`work/`、`target/`、`demo-run/` 均为运行时目录，已 gitignore。
