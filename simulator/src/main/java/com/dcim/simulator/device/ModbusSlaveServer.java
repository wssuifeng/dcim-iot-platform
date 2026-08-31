package com.dcim.simulator.device;

import lombok.extern.slf4j.Slf4j;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modbus TCP slave server：每设备一个独立端口，仅实现 FC03 读保持寄存器（MBAP+PDU 最小子集）
 *
 * 帧格式：
 *   请求 [txId:2][protoId:2][length:2][unitId:1][fc:1=0x03][startAddr:2][quantity:2]
 *   响应 [txId:2][protoId:2][length:2][unitId:1][fc:1=0x03][byteCount:1][registers:byteCount]
 * 异常响应 fc|0x80 + 异常码（01 非法功能 / 02 非法地址 / 03 非法数值）。
 * SENSOR_DEAD 故障 = 停止 server 响应（网关轮询超时判离线，与真实设备失联同构）。
 */
@Slf4j
public class ModbusSlaveServer {

    private final long deviceId;
    private final int port;
    private final RegisterStore registerStore;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean responding = new AtomicBoolean(true);
    private ServerSocket serverSocket;

    private static final int MAX_QUANTITY = 125;
    /** 本仿真器固定从站地址 1（网关轮询与预设寄存器映射均按此约定） */
    private static final int UNIT_ID = 1;
    private static final int EXC_ILLEGAL_FUNCTION = 0x01;
    private static final int EXC_ILLEGAL_ADDRESS = 0x02;
    private static final int EXC_ILLEGAL_VALUE = 0x03;
    private static final int EXC_GATEWAY_TARGET_FAILED = 0x0B;

    public ModbusSlaveServer(long deviceId, int port, RegisterStore registerStore) {
        this.deviceId = deviceId;
        this.port = port;
        this.registerStore = registerStore;
    }

    /**
     * 启动监听：每连接一线程（设备数量小，够用）
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running.set(true);
        responding.set(true);
        Thread acceptor = new Thread(() -> {
            while (running.get()) {
                try {
                    Socket socket = serverSocket.accept();
                    // 空闲读超时：须大于真实 master 的常见轮询周期（10s 会掐死 15~60s 周期的持久连接）
                    socket.setSoTimeout(60_000);
                    new Thread(() -> serve(socket), "modbus-slave-conn-" + port).start();
                } catch (IOException ex) {
                    if (running.get()) {
                        log.warn("slave accept 异常 port={} error={}", port, ex.getMessage());
                    }
                }
            }
        }, "modbus-slave-accept-" + port);
        acceptor.setDaemon(true);
        acceptor.start();
        log.info("Modbus slave 启动 deviceId={} port={}", deviceId, port);
    }

    /**
     * 停止监听（SENSOR_DEAD 注入即调用此方法）
     */
    public void stop() {
        running.set(false);
        responding.set(false);
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        log.info("Modbus slave 停止 deviceId={} port={}", deviceId, port);
    }

    /**
     * SENSOR_DEAD 恢复：重新监听
     */
    public void resume() throws IOException {
        if (!running.get()) {
            start();
        } else {
            responding.set(true);
        }
    }

    /**
     * 供测试与观测：是否仍在响应
     */
    public boolean isResponding() {
        return responding.get();
    }

    private void serve(Socket socket) {
        try (socket; DataInputStream in = new DataInputStream(socket.getInputStream());
             DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            while (running.get() && !socket.isClosed()) {
                // MBAP 头 7 字节 + 帧（length = unitId + PDU 的字节数）；先读完整帧再校验——保证流不错位
                int txId = in.readUnsignedShort();
                int protoId = in.readUnsignedShort();
                int length = in.readUnsignedShort();
                if (protoId != 0 || length < 2) {
                    return;
                }
                byte[] frame = new byte[length];
                in.readFully(frame);
                int unitId = frame[0] & 0xFF;
                int fc = frame.length > 1 ? frame[1] & 0xFF : 0;
                // 从站地址校验（主流 master 依赖确定性语义：未知从站按 TCP 网桥语义回 0x0B）
                if (unitId != UNIT_ID) {
                    writeException(out, txId, unitId, fc, EXC_GATEWAY_TARGET_FAILED);
                    continue;
                }
                if (fc != 0x03) {
                    writeException(out, txId, unitId, fc, EXC_ILLEGAL_FUNCTION);
                    continue;
                }
                // FC03 帧共 6 字节（unitId + fc + startAddr2 + quantity2）
                if (frame.length < 6) {
                    writeException(out, txId, unitId, fc, EXC_ILLEGAL_VALUE);
                    continue;
                }
                int startAddr = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
                int quantity = ((frame[4] & 0xFF) << 8) | (frame[5] & 0xFF);
                if (quantity < 1 || quantity > MAX_QUANTITY) {
                    writeException(out, txId, unitId, fc, EXC_ILLEGAL_VALUE);
                    continue;
                }
                // 地址越界：规范要求回 0x02（Illegal Data Address），而不是静默填 0
                if (startAddr + quantity > registerStore.sizeOf(deviceId)) {
                    writeException(out, txId, unitId, fc, EXC_ILLEGAL_ADDRESS);
                    continue;
                }
                // SENSOR_DEAD：挂起响应让 master 超时（真实失联语义）
                if (!responding.get()) {
                    return;
                }
                int[] regs = registerStore.read(deviceId, startAddr, quantity);
                byte[] respPdu = new byte[2 + quantity * 2];
                respPdu[0] = 0x03;
                respPdu[1] = (byte) (quantity * 2);
                for (int i = 0; i < quantity; i++) {
                    respPdu[2 + i * 2] = (byte) (regs[i] >> 8);
                    respPdu[3 + i * 2] = (byte) regs[i];
                }
                writeResponse(out, txId, unitId, respPdu);
            }
        } catch (IOException ignored) {
            // 客户端断开属正常轮询生命周期
        }
    }

    private void writeResponse(DataOutputStream out, int txId, int unitId, byte[] pdu) throws IOException {
        out.writeShort(txId);
        out.writeShort(0);
        out.writeShort(pdu.length + 1);
        out.writeByte(unitId);
        out.write(pdu);
        out.flush();
    }

    private void writeException(DataOutputStream out, int txId, int unitId, int fc, int code) throws IOException {
        out.writeShort(txId);
        out.writeShort(0);
        out.writeShort(3);
        out.writeByte(unitId);
        out.writeByte(fc | 0x80);
        out.writeByte(code);
        out.flush();
    }
}
