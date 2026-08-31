package com.dcim.simulator.gateway;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数据解码器：寄存器 raw → 工程量（raw / scale，int16 符号还原）
 */
public final class DataDecoder {

    private DataDecoder() {
    }

    /**
     * 解码单个寄存器值：按 int16 还原符号后除以缩放系数
     */
    public static BigDecimal decode(int rawUnsigned, double scale) {
        int signed = (short) rawUnsigned;
        double s = scale == 0 ? 1 : scale;
        return BigDecimal.valueOf(signed / s).setScale(4, RoundingMode.HALF_UP);
    }
}
