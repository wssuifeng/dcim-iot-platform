package com.dcim.simulator.gateway;

import com.dcim.simulator.device.ModbusSlaveServer;
import com.dcim.simulator.device.RegisterStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ServerSocket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Modbus TCP 帧级往返测试：真实 localhost socket，slave 与 master 同一最小子集对拍
 */
class ModbusRoundTripTest {

    private ModbusSlaveServer slave;
    private final RegisterStore store = new RegisterStore();
    private final ModbusMaster master = new ModbusMaster();

    private int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @AfterEach
    void tearDown() {
        if (slave != null) slave.stop();
    }

    @Test
    void readHoldingRegistersRoundTrip() throws Exception {
        int port = freePort();
        store.initDevice(1L, 4);
        store.write(1L, 0, 225);   // 22.5℃ × 10
        store.write(1L, 1, 653);   // 65.3% × 10
        store.write(1L, 2, -1200); // 负值按 int16 截断（-120.0）
        slave = new ModbusSlaveServer(1L, port, store);
        slave.start();
        Thread.sleep(200);

        int[] regs = master.readHoldingRegisters("127.0.0.1", port, 1, 0, 3);

        assertThat(regs).hasSize(3);
        assertThat((short) regs[0]).isEqualTo((short) 225);
        assertThat((short) regs[1]).isEqualTo((short) 653);
        assertThat((short) regs[2]).isEqualTo((short) -1200);
    }

    @Test
    void outOfRangeAddressReturnsIllegalDataAddressException() throws Exception {
        int port = freePort();
        store.initDevice(1L, 4); // 有效地址 0..3
        slave = new ModbusSlaveServer(1L, port, store);
        slave.start();
        Thread.sleep(200);

        // 越界地址：规范要求回 0x02 Illegal Data Address，而不是静默填 0
        byte[] raw = rawRequest(port, (byte) 1, (byte) 0x03, 500, 1);
        assertThat(raw[raw.length - 1]).as("异常码").isEqualTo((byte) 0x02);
        assertThat(raw[raw.length - 2]).as("fc|0x80").isEqualTo((byte) (0x03 | 0x80));
    }

    @Test
    void unknownUnitIdReturnsGatewayTargetException() throws Exception {
        int port = freePort();
        store.initDevice(1L, 4);
        slave = new ModbusSlaveServer(1L, port, store);
        slave.start();
        Thread.sleep(200);

        // 错误从站地址：按 TCP 网桥语义回 0x0B（目标从站未响应），不返回本设备数据
        byte[] raw = rawRequest(port, (byte) 99, (byte) 0x03, 0, 1);
        assertThat(raw[raw.length - 1]).as("异常码").isEqualTo((byte) 0x0B);
    }

    /** 手拼一帧 FC03 请求并读回完整响应（绕过自家 master，验证协议语义本身） */
    private byte[] rawRequest(int port, byte unitId, byte fc, int startAddr, int quantity)
            throws IOException {
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream());
            java.io.DataInputStream in = new java.io.DataInputStream(socket.getInputStream());
            out.writeShort(1);          // txId
            out.writeShort(0);          // protoId
            out.writeShort(6);          // length
            out.writeByte(unitId);
            out.writeByte(fc);
            out.writeShort(startAddr);
            out.writeShort(quantity);
            out.flush();
            in.readUnsignedShort();      // txId
            in.readUnsignedShort();      // protoId
            int length = in.readUnsignedShort();
            byte[] frame = new byte[length];
            in.readFully(frame);         // 线序：unitId + PDU
            return frame;
        }
    }

    @Test
    void sameConnectionSequenceKeepsStreamInSync() throws Exception {
        int port = freePort();
        store.initDevice(1L, 4);
        store.write(1L, 0, 225);
        slave = new ModbusSlaveServer(1L, port, store);
        slave.start();
        Thread.sleep(200);

        // 回归：unitId 异常曾不消费剩余请求字节导致流错位，后续请求全被拒、连接被断
        try (java.net.Socket socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            java.io.DataOutputStream out = new java.io.DataOutputStream(socket.getOutputStream());
            java.io.DataInputStream in = new java.io.DataInputStream(socket.getInputStream());

            rawWrite(out, 1, (byte) 1, 0, 1);
            assertThat(in.readUnsignedShort()).isEqualTo(1);
            in.readUnsignedShort();
            assertThat(in.readUnsignedShort()).isEqualTo(5);
            assertThat(in.readUnsignedByte()).isEqualTo(1);
            assertThat(in.readUnsignedByte()).isEqualTo(0x03);
            assertThat(in.readUnsignedByte()).isEqualTo(2);
            assertThat(in.readUnsignedShort()).isEqualTo(225);

            rawWrite(out, 2, (byte) 99, 0, 1);
            assertThat(in.readUnsignedShort()).isEqualTo(2);
            in.readUnsignedShort();
            assertThat(in.readUnsignedShort()).isEqualTo(3);
            assertThat(in.readUnsignedByte()).isEqualTo(99);
            assertThat(in.readUnsignedByte()).isEqualTo(0x03 | 0x80);
            assertThat(in.readUnsignedByte()).isEqualTo(0x0B);

            rawWrite(out, 3, (byte) 1, 500, 1);
            assertThat(in.readUnsignedShort()).isEqualTo(3);
            in.readUnsignedShort();
            assertThat(in.readUnsignedShort()).isEqualTo(3);
            assertThat(in.readUnsignedByte()).isEqualTo(1);
            assertThat(in.readUnsignedByte()).isEqualTo(0x03 | 0x80);
            assertThat(in.readUnsignedByte()).isEqualTo(0x02);

            rawWrite(out, 4, (byte) 1, 0, 1);
            assertThat(in.readUnsignedShort()).isEqualTo(4);
            in.readUnsignedShort();
            assertThat(in.readUnsignedShort()).isEqualTo(5);
            assertThat(in.readUnsignedByte()).isEqualTo(1);
            assertThat(in.readUnsignedByte()).isEqualTo(0x03);
            assertThat(in.readUnsignedByte()).isEqualTo(2);
            assertThat(in.readUnsignedShort()).isEqualTo(225);
        }
    }

    private void rawWrite(java.io.DataOutputStream out, int txId, byte unitId, int addr, int qty)
            throws IOException {
        out.writeShort(txId);
        out.writeShort(0);
        out.writeShort(6);
        out.writeByte(unitId);
        out.writeByte(0x03);
        out.writeShort(addr);
        out.writeShort(qty);
        out.flush();
    }

    @Test
    void singleRegisterDecodeToEngineeringValue() throws Exception {
        int port = freePort();
        store.initDevice(2L, 1);
        store.write(2L, 0, 225);
        slave = new ModbusSlaveServer(2L, port, store);
        slave.start();
        Thread.sleep(200);

        int[] regs = master.readHoldingRegisters("127.0.0.1", port, 1, 0, 1);
        BigDecimal value = DataDecoder.decode(regs[0], 10.0);

        assertThat(value.doubleValue()).isEqualTo(22.5);
    }

    @Test
    void stoppedSlaveTimesOutAsOffline() throws Exception {
        int port = freePort();
        store.initDevice(3L, 1);
        slave = new ModbusSlaveServer(3L, port, store);
        slave.start();
        Thread.sleep(200);
        slave.stop(); // SENSOR_DEAD 语义

        assertThatThrownBy(() -> master.readHoldingRegisters("127.0.0.1", port, 1, 0, 1))
                .isInstanceOf(IOException.class);
    }

    @Test
    void registerWriteTruncatesToInt16() {
        store.initDevice(4L, 1);
        store.write(4L, 0, 70000); // 超 int16 范围截断
        int[] regs = store.read(4L, 0, 1);
        assertThat((short) regs[0]).isEqualTo((short) 70000);
    }
}
