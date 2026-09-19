# R1 · 启动与体积基准

> 用途：为「纯 JDK 零依赖 + 挑战 Go/Rust/C++ 启动速度」这一主张提供**可复现的实测底数**。
> 状态：`wip`（未送审）。native 基准属 R2，本文件只测 JVM 直跑。

## 1 环境

| 项 | 值 |
|---|---|
| CPU / OS | Windows 11 10.0 / amd64 |
| JDK | Oracle 25.0.4 |
| 构建 | Maven Wrapper，`./mvnw -q package` |
| 产物 | `target/jagent.jar`，固定名 `jagent` |

## 2 方法

- 计时用 `python` 的 `time.perf_counter()` 包住一次完整进程调用，含 **JVM 冷启动 + 类加载 + 执行 + 退出**，即用户实际感知的墙钟时间。
- 每个命令跑 **7 次**，取 min / median / max。首次运行可能受文件系统缓存影响，故保留 min 与 max 以显示离散度。
- 被测命令选**最短路径**（不做网络、不做业务）：
  - `java -jar target/jagent.jar version` —— 最短可执行路径，衡量纯启动开销。
  - `java -jar target/jagent.jar help` —— 触发资源加载与语言包装配。
- 体积取 jar 文件字节数。

## 3 结果

| 指标 | min | median | max |
|---|---|---|---|
| `version` 墙钟 (ms) | 69 | **70** | 97 |
| `help` 墙钟 (ms) | 68 | **70** | 74 |

| 指标 | 值 |
|---|---|
| jar 体积 | 131,864 B ≈ **129 KiB** |
| 第三方运行时依赖 | **0** |

## 4 结论

- 冷启动中位数 **70 ms**，与 `help` 持平，说明**资源加载与语言包装配几乎不产生额外开销**（两者都在同一量级内）。
- 体积 **129 KiB**，其中只有本项目自己的 class 与两份 `.properties`，**没有一个第三方 jar**。这正是硬性约定 #1 换来的结果——零依赖才使得体积与冷启动同时可压。
- 7 次采样的 min/max 差距（69–97ms）主要来自 JVM 首次磁盘读取与后台进程干扰，不是程序行为差异。

## 5 局限（不得越界解读）

1. 本表是 **JVM 直跑**数据，**不含** native-image 结果；native 属 R1 范围外（推迟 R2），
   届时需另测并追加到本文件，不能拿 JVM 数字冒充 native 数字。
2. 未与 Go / Rust / C++ 的同类工具做**同机同法**对照。在完成对照前，
   README 中「进入同一量级竞争」只作为**目标**陈述，不作为已证结论。
3. 未测**常驻内存**（RSS）。三档目标中的性能主张若要成立，该项必须补测。

## 6 复现

```bash
./mvnw -q package
java -jar target/jagent.jar version   # 观察单次墙钟
```

批量采样脚本见 `R1_92_logs.txt` 对应条目所述方法（python `perf_counter` × 7）。
