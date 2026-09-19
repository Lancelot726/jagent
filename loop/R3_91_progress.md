# 进度 · jagent

状态：R3 进行中 · 当前节点 S1（口径已锁，待作者确认后进 S2 备料 / S3 架构）

## 跨轮总览

```mermaid
flowchart LR
    R1["R1 首轮：可运行 demo<br/>骨架/图/并行/记忆<br/>2026-09-19 已定稿"] --> R2["R2 双厂商接入<br/>四组机制真实验证<br/>2026-09-20 已定稿"]
    R2 --> R3["R3 终端交互与显示口径<br/>REPL / 摘要呈现 / 标签中性化<br/>本轮"]
    R3 --> R4["R4 待触发<br/>native/性能基准"]

    classDef done fill:#b7e4c7,stroke:#2d6a4f
    classDef cur fill:#ffd166,stroke:#b8860b
    classDef todo fill:white,stroke:#999,stroke-dasharray:5 5

    class R1,R2 done
    class R3 cur
    class R4 todo
```

## 当轮细图 R3

```mermaid
flowchart TD
    S0["S0 收件"] --> S1["S1 建轮"]
    S1 --> S2["S2 备料"]
    S2 --> S3["S3 架构"]
    S3 --> S4["S4 编码"]
    S4 --> S5["S5 验证"]
    S5 --> S6["S6 送审"]
    S6 -->|"rev1 → rev2"| S5
    S6 --> S7["S7 定稿"]
    S7 --> S8["S8 归档"]

    G1(("G1 口径")) -.-> S1
    G2(("G2 缺料")) -.-> S2
    G3(("G3 人实跑")) -.-> S6
    G4(("G4 拍板")) -.-> S7

    classDef done fill:#b7e4c7,stroke:#2d6a4f
    classDef cur fill:#ffd166,stroke:#b8860b
    classDef todo fill:white,stroke:#999,stroke-dasharray:5 5

    class S0 done
    class S1 cur
    class S2,S3,S4,S5,S6,S7,S8 todo
```

## 闸门

| 闸门 | 内容 | 状态 |
|---|---|---|
| G1 口径确认 | 四项范围 / 改动层次 / 换词幅度 / REPL 形态 / 进化论去留 | **已问**（作者 2026-09-20 已答全部五项，见 `R3_10` §2；待作者过目 `R3_10` 后正式过闸） |
| G2 缺料求援 | 本轮暂未见缺料 | 待 |
| G3 人工实跑 | 作者在本机真终端试 REPL 与新的摘要呈现 | 待 |
| G4 人工定稿 | 冻结 R3 | 待 |

## 编码阶段进度

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 机制标签中性化（六词转固定英文，只改显示层） | 未开工 |
| P1 | 执行图改画结构与状态、末尾统一总结 | 未开工 |
| P2 | `chat` 推理与答案分行 | 未开工 |
| P3 | REPL 基础版（提示符 + 读一行跑一行） | 未开工 |
| P4 | lang 键中英对称性回归 | 未开工 |
| P5 | 断言（新增 + R1/R2 全套回归 458） | 未开工 |
| P6 | mock 端到端自测 | 未开工 |
| P7 | 真实厂商复跑（沿用 `work/jagent.properties` 两档） | 未开工 |

## 阻塞项

- 无。
