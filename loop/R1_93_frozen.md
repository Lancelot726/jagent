# 冻结清单 · jagent

> 记录已拍板成为下一轮基线的文件及其校验值。`fin` 件从此只读。

## 冻结件

（无。R1 尚无经人工拍板的定稿件。）

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

**仍未做**

| 项 | 说明 |
|---|---|
| `.git` 仓库初始化 | 待 G4 拍板通过后执行 |
| G4 人工定稿 | 决定是否发布；当前闸门 |

G3 真终端实跑已于 2026-09-19 通过（原地刷新 / 字形整齐 / 对齐正常），详见 `R1_94_rounds.md`。

`work/` 为运行时目录，gitignore。仓库尚未 `git init`（待 G4 拍板后执行）。
