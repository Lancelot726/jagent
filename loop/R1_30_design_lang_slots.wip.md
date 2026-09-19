# R1 · 语言槽位机制设计

> 来源：作者在 R1 执行中途提出的修正 —— `[]` 里的语言是**可选项**，不是写死的常量。
> 本文是该修正的落地设计，同时是 `R1_90_req_constitution.md` §3.5 的展开。

## 1 问题

初版把系统提示词写成固定中文：

```
## Internal — Eng abbr
...
## User-facing — Zh
```

其中 `Zh` 是硬编码的。作者指出：**这个位置是用户可选的**（Eng / Zh），
用户首次启动时选定，其选择被写进 `[]`。这条修正**同时作用于两类文件**：

| 文件类别 | 要改什么 |
|---|---|
| 产品代码与资源 | 提示词变成模板；所有面向用户的串走语言包 |
| `loop/` 过程文件 | 宪法与任务书不得再把"中文"写成常量，要表述为"用户选定语言" |

## 2 两层语言模型

| 层 | 语言 | 可否选择 | 理由 |
|---|---|---|---|
| **L1 内部层** | 英文 + 极强缩略（固定） | **不可选** | 内部思考/日志/进度追求最短 token；缩略语法只在英文里成立，换语言即失效 |
| **L2 面向层** | `{{user_lang}}` ∈ {Zh, Eng} | **用户首启选定** | 交付物、问答、结果汇报要让用户读得舒服 |

关键点：**缩略是内部层的属性，不是中文的属性**。所以"极强缩略"绑定在 L1，
而 L1 永远是英文；L2 才有语言选项。两层各管一段，互不污染。

## 3 槽位协议

提示词资源中的语言槽位统一写作 `{{user_lang}}`：

```
## Internal — Eng abbr
1. Think: ≤1 sent, abbr.
...
## User-facing — {{user_lang}}
6. Opts/Qs -> {{user_lang}}.
...
```

装配时做一次纯字符串替换：`{{user_lang}}` → `Lang.tag()`（`Zh` / `Eng`）。
替换是**幂等的纯函数**，所以同一份提示词前缀在整轮对话中逐字不变 → 命中供应商前缀缓存。

## 4 解析优先级

```
--user-lang <zh|eng>           命令行，最高
JAGENT_USER_LANG=<zh|eng>      环境变量
work/jagent.properties         持久化选择（由首启写入）
（首次运行且为交互终端）         打印双语选择菜单，读入数字，落盘
Lang.ZH                        兜底默认
```

首启菜单用 `Lang.ENG` 渲染框架、中英并列，避免"还没选语言就用某种语言提问"的自指。

## 5 目录与扩展规则

```
src/main/resources/
├── prompt/system.txt      含 {{user_lang}} 槽位的模板（唯一一份，不按语言分叉）
└── lang/
    ├── zh.properties
    └── eng.properties
```

- 提示词**只有一份模板**，语言靠替换；不复制成 `system.zh.txt` / `system.eng.txt`，
  否则两处漂移。
- 界面串**按语言分文件**，因为它们是不同语言的自然文本，无法靠替换生成。
- **加一门语言 = 加一个 `lang/<code>.properties` + `Lang` 一枚枚举 + `Lang.of()` 一条映射**，
  不动任何业务代码。

## 6 落地清单

| 位置 | 改动 |
|---|---|
| `resources/prompt/system.txt` | 由硬编码 `Zh` 改为模板 `{{user_lang}}` |
| `resources/lang/*.properties` | 新增，约 40 键 × 2 语言 |
| `jagent/term/Lang.java` | 枚举 + 资源加载 + `t(key, args)` + `of()` |
| `Config` | 增 `lang` 字段、`resolveLang()`、`save()`、`systemPrompt()` |
| `Cli` | 全部面向用户串改走 `L.t(...)`，新增 `--user-lang` |
| `R1_90` 硬性约定 #5 | 由"输出中文"改为"输出用户选定语言" |
| `R1_03_style_prompt.in.txt` | **不动**（只增不改铁律，原稿是只读输入） |

## 7 验证

`target/t/P1T.java` 中三条断言固定该机制：

```
tpl.has.slot        -> 模板含 {{user_lang}}
tpl.zh / tpl.eng    -> 替换后分别得到 Zh 段 / Eng 段
lang.of.zh|eng      -> 标识串映射正确
```

端到端：`help`（默认 zh）与 `help --user-lang eng` 分别输出中/英，`doctor` 报出 `user lang: eng / Eng`。
