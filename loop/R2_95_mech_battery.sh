#!/usr/bin/env bash
# R2 机制验证电池: 逐个逼出 R1 的四组机制, 各自一块对账单
# 用法: bash loop/R2_95_mech_battery.sh [workdir]
# 输出: 每场景一节 -> 追加到 stdout, 供重定向留证

set -u
JAR="$(cd "$(dirname "$0")/.." && pwd)/target/jagent.jar"
ROOT="${1:-target/battery}"
rm -rf "$ROOT"
mkdir -p "$ROOT"

strip() { sed -e 's/\x1b\[[0-9;]*[A-Za-z]//g'; }

say() { printf '\n===== %s =====\n' "$1"; }

run() {
  local name="$1"; shift
  say "$name"
  java -jar "$JAR" run "$name" --cwd "$ROOT/$name" --no-graph "$@" 2>&1 \
    | strip \
    | grep -E '竞价|步数|拍卖:|信誉:|压缩:|控制论:|降级:|执行图:|路径互斥:'
  echo "--- work/scorecard.txt ---"
  strip < "$ROOT/$name/work/scorecard.txt" 2>/dev/null || echo "(no scorecard)"
}

[ -f "$JAR" ] || { echo "先构建: ./mvnw -q -o package"; exit 1; }

cat <<'HDR'
# R2 机制验证电池 · 证据
# 目的: 一次跑完 R1 的四组机制, 每个场景尾部打印同一块"机制对账"
# 场景: A 基线 / B 预算压力 / C 压缩ROI / D 控制论 / E 故障注入
# 复现: bash loop/R2_95_mech_battery.sh target/battery > 本文件
# 说明: 本文件由脚本生成, 重跑即覆盖; 结论写在 R2_10 任务书与 R2_94 台账
HDR

say "A 基线: 拍卖 + 预算 + 图 (无额外参数)"
run a_baseline --provider mock

say "B 预算压力: 逼拍卖翻转到廉价档"
run b_pressure --provider mock --budget 1000 --permits 1

say "C 压缩 ROI: 低阈值逼出压缩决策"
run c_compress --provider mock --compress-after 64

say "D 控制论: 单许可 + 工具延迟, 看并发峰值与许可上调"
run d_control --provider mock --permits 1 --tool-delay 120

say "E 故障注入: 假地址逼出重试 + 熔断 + 信誉下调"
run e_fault --provider openai --base-url http://127.0.0.1:9/v1 --api-key k --model dead

say "电池结束"
