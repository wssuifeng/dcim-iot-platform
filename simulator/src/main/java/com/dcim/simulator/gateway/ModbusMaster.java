package com.dcim.simulator.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Modbus TCP master：FC03 读保持寄存器（与 slave 相同的最小子集，超时判定设备离线）
 *
 * 每次 poll 新建短连接（轮询周期 5s 量级下开销可忽略，且免连接状态机）。
 */
@Slf4j
@Component
public class ModbusMaster {

    private static final int DEFAULT_TIMEOUT_MS = 2000;
    private static final AtomicInteger TX_SEQ = new AtomicInteger(1);

    /**
     * 读保持寄存器；失败/超时抛 IOException（调用方据此判设备离线）
     */
    public int[] readHoldingRegisters(String host, int port, int unitId,
                                      int startAddr, int quantity) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), DEFAULT_TIMEOUT_MS);
            socket.setSoTimeout(DEFAULT_TIMEOUT_MS);
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            int txId = TX_SEQ.getAndIncrement() & 0xFFFF;
            out.writeShort(txId);
            out.writeShort(0);
            out.writeShort(6);
            out.writeByte(unitId);
            out.writeByte(0x03);
            out.writeShort(startAddr);
            out.writeShort(quantity);
            out.flush();

            int respTx = in.readUnsignedShort();
            if (respTx != txId) {
                throw new IOException("事务标识不匹配: " + respTx + " != " + txId);
            }
            int protoId = in.readUnsignedShort();
            int length = in.readUnsignedShort();
            if (protoId != 0) {
                throw new IOException("协议标识非0: " + protoId);
            }
            in.readUnsignedByte(); // unitId
            int fc = in.readUnsignedByte();
            if ((fc & 0x80) != 0) {
                int code = in.readUnsignedByte();
                throw new IOException("Modbus 异常响应 fc=0x" + Integer.toHexString(fc) + " code=" + code);
            }
            int byteCount = in.readUnsignedByte();
            if (byteCount != quantity * 2 || length != byteCount + 3) {
                throw new IOException("响应长度不匹配 byteCount=" + byteCount);
            }
            int[] regs = new int[quantity];
            for (int i = 0; i < quantity; i++) {
                regs[i] = in.readShort() & 0xFFFF;
                // 读为无符号 16 位（寄存器语义），解码侧负责符号还原
            }
            return regs;
        }
    }
}
