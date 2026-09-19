# R1 任务书

## 1 本轮触发

| 来源 | 文件 |
|---|---|
| 需求原文 | `R1_00_req_product.in.txt` |
| 系统提示词原稿 | `R1_03_style_prompt.in.txt` |
| 设计思路 | 同上两份，本文件不重复摘录，引用 §3 硬性约定 |

## 2 口径锁定（G1，已过）

名称 `jagent` · 包根 `jagent` · 纯 JDK 零依赖 · OpenAI 兼容协议 ·
Maven Wrapper + 预留 native profile · Java 25 · demo 四项全选。

**执行中途追加口径（作者提出，已纳入宪法 #5）**：提示词 `[]` 中的语言是**用户可选项**。
L1 内部层固定英文 + 极强缩略；L2 面向层由用户首启选定 Zh/Eng，选择被写进 `[]` 槽位。
该口径作用于产品代码、资源、`loop/` 过程文件与 `docs/`。设计见
`R1_30_design_lang_slots.wip.md`。

## 3 流程适配说明（对本 skill 的两处偏离）

| 偏离 | skill 原要求 | 本项目做法 | 理由 |
|---|---|---|---|
| 目录 | 过程文件在根目录平铺 | 收进 `loop/`，**其内部仍平铺** | Java 项目根目录要放 `pom.xml`/`src/`；公开仓库首页不宜堆二十个过程文件。轮次仍由文件名前缀表达，保留"按名排序即按流程排序" |
| 命名范围 | 全部交付物用 `R<N>_<槽位>_<语义名>_<状态>` | **Java 源码不进轮次命名**，由 git 管版本；轮次体系只管过程文档与交付物 | 源码在 `src/` 下受包名约束，无法承载轮次前缀 |

其余全部保留：轮次制、命名法、四个闸门、`90`–`94` 记录文件互不复制、两层进度图、只增不改。

## 4 节点语义映射

```
S0 收件 → S1 建轮 → S2 调研 → S3 架构 → S4 编码 → S5 验证 → S6 送审 → S7 定稿 → S8 归档
                  G2 缺料      G1 口径      G3 人实跑      G4 拍板
```

**G3 在本项目 = 作者在本机终端实跑 demo**。理由：Unicode 图渲染是否错位、
仪表盘是否闪烁、Windows 中文控制台的 UTF-8 是否正常，这三件事无法在无终端的环境里验证。

## 5 本轮要建的槽位（B 批，用不到的不建）

`10` 任务书 · `20` 调研 · `30` 架构 · `50` 基准 · `90`–`94` 元层。
`40` 原型、`60` 冻结件：用到才建。

已建：`R1_30_design_lang_slots.wip.md`（语言槽位机制设计，执行中途追加口径的落地件）。

## 6 执行流水

| 阶段 | 内容 | 验证标准 |
|---|---|---|
| P0 | wrapper + pom + Main/Cli/Config + Json/Sse + `doctor` | `mvnw -q package` 成功；`doctor` 报出终端编码/宽度 |
| P1 | `llm` 包 + `MockClient` | mock 流式；工具参数增量拼接正确 |
| P2 | `tool` 包 + `Agent` + `SystemPrompt` | 自主完成"建文件→写入→读回" |
| P3 | `graph`/`core`/`term` | 实时 Unicode 树；两个工具调用同时 RUNNING |
| P4 | `SpawnTool` + `Auction` + `Budget` + `Governor` | 竞价行打印；并发度随错误率升降 |
| P5 | `mem` 包 + `MemoryTool` + `HumanTool` | `work/` 长出协议文件；token 先涨后跌；重启续跑 |

跑不通不进下一阶段。

## 7 待作者提供（不阻塞 P0–P3）

`JAGENT_BASE_URL` / `JAGENT_API_KEY` / `JAGENT_MODEL`，或 `work/jagent.properties`。
不进代码、不进 git。
