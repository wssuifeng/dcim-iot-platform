package com.dcim.simulator.device;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 寄存器仓库：每设备一份 int16 保持寄存器表，设备模块写入、slave server 读取
 */
@Component
public class RegisterStore {

    private final Map<Long, int[]> registersByDevice = new ConcurrentHashMap<>();

    /**
     * 初始化设备的寄存器表（地址 0..size-1）
     */
    public void initDevice(Long deviceId, int size) {
        registersByDevice.put(deviceId, new int[size]);
    }

    /**
     * 写入单个寄存器（值按 int16 截断，还原真实设备寄存器语义）
     */
    public void write(Long deviceId, int address, int value) {
        int[] regs = registersByDevice.get(deviceId);
        if (regs != null && address >= 0 && address < regs.length) {
            regs[address] = (short) value;
        }
    }

    /**
     * 批量读寄存器（FC03 服务入口），越界地址填 0
     */
    public int[] read(Long deviceId, int startAddr, int quantity) {
        int[] regs = registersByDevice.get(deviceId);
        int[] out = new int[quantity];
        if (regs != null) {
            for (int i = 0; i < quantity; i++) {
                int addr = startAddr + i;
                if (addr >= 0 && addr < regs.length) {
                    out[i] = regs[addr];
                }
            }
        }
        return out;
    }

    /**
     * 寄存器表长度（0..size-1）；设备未初始化返回 0（供 FC03 地址越界判定）
     */
    public int sizeOf(Long deviceId) {
        int[] regs = registersByDevice.get(deviceId);
        return regs == null ? 0 : regs.length;
    }

    /**
     * 移除设备寄存器表
     */
    public void removeDevice(Long deviceId) {
        registersByDevice.remove(deviceId);
    }
}
