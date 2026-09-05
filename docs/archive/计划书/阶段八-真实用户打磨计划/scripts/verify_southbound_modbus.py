#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
verify_southbound_modbus.py — 南向协议一致性验证（pymodbus 作为第三方 master 直连仿真器）
来源：阶段七/八主流工具实测（2026-08-30），曾抓到 3 个 slave 协议缺陷（见 00-问题总账 F1~F3）。

用法:
  python verify_southbound_modbus.py [--sim http://localhost:8090]
说明:
  - 值比对目标为机房 4 的 TH-01/UPS-01/PDU-01/AC-01（端口由 /api/sim/devices 动态解析），
    指标映射按设备类型静态对齐 DevicePresetCatalog（前两个寄存器）；
  - 边界用例：多寄存器读取、未知从站 0x0B、越界地址 0x02（2026-08-30 修复后语义）；
  - 退出码 0=全过，1=有失败（可作 CI 断言）。
"""
import argparse
import json
import socket
import struct
import urllib.request
from pymodbus.client import ModbusTcpClient

CODES = ["TH-01", "UPS-01", "PDU-01", "AC-01"]
# 各设备类型前两个寄存器的 (pointCode, scale)——与 DevicePresetCatalog/device_register_map 对齐
TYPE_REGMAP = {
    4: [("temperature", 10), ("humidity", 10)],
    1: [("voltage_a", 10), ("current_a", 10)],
    2: [("voltage_a", 10), ("current_a", 10)],
    3: [("supply_air_temp", 10), ("return_air_temp", 10)],
}


def sim_get(url):
    with urllib.request.urlopen(url, timeout=10) as resp:
        return json.loads(resp.read().decode())


def raw_xact(port, unit, addr, qty=1):
    """裸 socket 发一帧 FC03 并读完整响应（绕开 pymodbus，直接断言协议语义）"""
    s = socket.create_connection(("127.0.0.1", port), timeout=3)
    s.sendall(struct.pack(">HHHBBHH", 1, 0, 6, unit, 3, addr, qty))
    s.settimeout(3)
    head = b""
    while len(head) < 7:
        chunk = s.recv(7 - len(head))
        if not chunk:
            s.close()
            return None
        head += chunk
    length = struct.unpack(">H", head[4:6])[0]
    rest = head[6:]
    while len(rest) < length:
        chunk = s.recv(length - len(rest))
        if not chunk:
            break
        rest += chunk
    s.close()
    return rest


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--sim", default="http://localhost:8090")
    args = ap.parse_args()

    devices = {d["deviceCode"]: d for d in sim_get(args.sim + "/api/sim/devices")}
    snap = sim_get(args.sim + "/api/sim/status")["latestValues"]

    results, fails = [], 0
    for code in CODES:
        dev = devices.get(code)
        if not dev:
            print(f"{code}: 仿真器中不存在，跳过")
            continue
        port = dev["modbusPort"]
        regmap = TYPE_REGMAP.get(dev.get("typeId"), [])
        client = ModbusTcpClient("127.0.0.1", port=port, timeout=3)
        assert client.connect(), f"{code}: 连接失败 port={port}"
        for addr, (point, scale) in enumerate(regmap):
            rr = client.read_holding_registers(address=addr, count=1, device_id=1)
            if rr.isError():
                print(f"{code}.{point}: 读取失败 {rr}")
                fails += 1
                continue
            raw = rr.registers[0]
            decoded = raw / scale
            # 网关观测按 pointCode 取（Java HashMap 序列化顺序不定，不能按字典序）
            gw_points = snap.get(code, {})
            gw_val = float(gw_points[point]) if point in gw_points else None
            ok = gw_val is not None and abs(decoded - gw_val) < 0.5
            results.append((code, point, raw, decoded, gw_val, ok))
            fails += 0 if ok else 1
        client.close()

    for code, point, raw, decoded, gw, ok in results:
        mark = "✓" if ok else "✗"
        print(f"{code:<8}{point:<16} raw={raw:<6} decoded={decoded:<10} gateway={gw} {mark}")

    # 边界用例（基于 TH-01）
    port = devices["TH-01"]["modbusPort"]
    client = ModbusTcpClient("127.0.0.1", port=port, timeout=3)
    client.connect()
    rr = client.read_holding_registers(address=0, count=2, device_id=1)
    multi_ok = not rr.isError() and len(rr.registers) == 2
    print(f"多寄存器 count=2: {'✓' if multi_ok else '✗'}")
    fails += 0 if multi_ok else 1
    client.close()

    tail = raw_xact(port, 99, 0)
    unit_ok = tail is not None and len(tail) >= 3 and tail[1] == 0x83 and tail[2] == 0x0B
    print(f"未知从站 0x0B: {'✓' if unit_ok else '✗ ' + (tail.hex() if tail else 'EOF')}")
    fails += 0 if unit_ok else 1

    tail = raw_xact(port, 1, 500)
    oob_ok = tail is not None and len(tail) >= 3 and tail[1] == 0x83 and tail[2] == 0x02
    print(f"越界地址 0x02: {'✓' if oob_ok else '✗ ' + (tail.hex() if tail else 'EOF')}")
    fails += 0 if oob_ok else 1

    print(f"\n结论：{'全部通过' if fails == 0 else str(fails) + ' 项失败'}")
    raise SystemExit(0 if fails == 0 else 1)


if __name__ == "__main__":
    main()
