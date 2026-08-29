#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
load-probe.py — 阶段七压测采样脚本
轮询仿真器 /api/sim/load-stats（客户端视角）与平台 /api/observability/ingest-metrics（服务端视角），
落 CSV 供压测报告汇总。

用法:
  python load-probe.py --duration 600 --interval 60 --label L2_highfreq --sim http://localhost:8090 --platform http://localhost:8081 --token <JWT>
输出:
  <label>_probe.csv（每行一个采样点，两视角合并）
"""
import argparse
import csv
import json
import time
import urllib.request
from datetime import datetime


def get_json(url, token=None):
    req = urllib.request.Request(url)
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(req, timeout=10) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--duration", type=int, default=600, help="采样窗口秒数")
    ap.add_argument("--interval", type=int, default=60, help="采样间隔秒数")
    ap.add_argument("--label", default="L1", help="档位标签")
    ap.add_argument("--sim", default="http://localhost:8090")
    ap.add_argument("--platform", default="http://localhost:8081")
    ap.add_argument("--token", required=True, help="平台 JWT（ADMIN/OPERATOR）")
    args = ap.parse_args()

    fname = f"{args.label}_probe.csv"
    with open(fname, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "ts", "label",
            "gateway_id", "room_id", "gw_name", "running", "report_interval", "poll_interval",
            "qps", "p50_ms", "p95_ms", "p99_ms",
            "http_requests", "http_success", "http_failed",
            "plat_accepted_delta", "plat_deduped_delta", "plat_failed_delta",
            "plat_batches_delta", "plat_p95_ms",
        ])
        f.flush()

        # 平台侧计数为累计值：采样脚本内部维护差分基线
        baseline = None
        end_at = time.time() + args.duration
        while time.time() < end_at:
            ts = datetime.now().strftime("%H:%M:%S")
            row = [ts, args.label]
            try:
                stats = get_json(f"{args.sim}/api/sim/load-stats")
                # 每网关一行（平台差分仅在首行计算，其余行留空）
                plat_delta = ["", "", "", "", ""]
                for idx, g in enumerate(stats):
                    r = ["", "", "", "", "", "", "", "", "", "", "", ""]
                    try:
                        s = g.get("p50Ms")
                        r = [g["gatewayId"], g.get("cloudRoomId"), g["name"],
                             g["running"], g.get("reportIntervalSec"), g.get("pollIntervalSec"),
                             g.get("qps"), g.get("p50Ms"), g.get("p95Ms"), g.get("p99Ms"),
                             g.get("httpRequests"), g.get("httpSuccess"), g.get("httpFailed")]
                    except Exception:
                        pass
                    writer.writerow([ts, args.label] + [str(x) for x in r] + plat_delta)
            except Exception as ex:
                writer.writerow([ts, args.label, "PROBE_ERROR(sim):", str(ex)[:80]])
            try:
                m = get_json(f"{args.platform}/api/observability/ingest-metrics", args.token)["data"]
                cur = (m["samplesAccepted"], m["samplesDeduped"], m["samplesFailed"], m["batchesTotal"])
                if baseline is None:
                    baseline = cur
                delta = [c - b for c, b in zip(cur, baseline)]
                writer.writerow([ts, args.label] + [""] * 13 + [str(d) for d in delta] + [str(m["latency"]["p95Ms"] or "")])
            except Exception as ex:
                writer.writerow([ts, args.label, "PROBE_ERROR(platform):", str(ex)[:80]])
            f.flush()
            time.sleep(args.interval)
    print(f"done -> {fname}")


if __name__ == "__main__":
    main()
