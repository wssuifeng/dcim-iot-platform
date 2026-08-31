package com.example.platform.ingest.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/*
 * 类功能:网关上报签名与幂等键计算工具（JDK SHA-256，无状态纯函数）
 *
 * 签名模型（与 AWS AccessKey/SecretKey 同构）：
 *   signature = hex(sha256(secret + rawBody))
 *   HTTP 放 Authorization: Gateway {accessKey}:{signature} 头；
 *   MQTT 报文放 signature 字段（对 batch JSON 字节签名）。
 * 幂等键：hex(sha256(deviceCode:pointCode:seq))。
 * */
public final class GatewaySignUtils {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private GatewaySignUtils() {
    }

    /*
     * 方法功能:计算上报签名 sha256(secret + rawBody) 的十六进制小写表示
     * 参数:secret-凭证签名密钥, rawBody-原始报文字节（JSON 序列化结果）
     * 返回数据:String-64位十六进制签名字符串
     * */
    public static String signature(String secret, byte[] rawBody) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[secretBytes.length + rawBody.length];
        System.arraycopy(secretBytes, 0, input, 0, secretBytes.length);
        System.arraycopy(rawBody, 0, input, secretBytes.length, rawBody.length);
        return sha256Hex(input);
    }

    /*
     * 方法功能:计算上报签名（字符串报文重载）
     * 参数:secret-凭证签名密钥, rawBody-原始报文字符串
     * 返回数据:String-64位十六进制签名字符串
     * */
    public static String signature(String secret, String rawBody) {
        return signature(secret, rawBody.getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 方法功能:校验签名是否匹配（恒时比较，防时序侧信道）
     * 参数:secret-凭证签名密钥, rawBody-原始报文字节, provided-网关侧提供的签名
     * 返回数据:boolean-匹配返回true
     * */
    public static boolean verify(String secret, byte[] rawBody, String provided) {
        if (provided == null || provided.length() != 64) {
            return false;
        }
        String expected = signature(secret, rawBody);
        // MessageDigest.isEqual 恒时比较
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 方法功能:计算幂等去重键 sha256(deviceCode:pointCode:seq) 的十六进制表示
     * 参数:deviceCode-设备编码, pointCode-指标编码, seq-网关侧单调递增序号
     * 返回数据:String-64位十六进制去重键
     * */
    public static String dedupKey(String deviceCode, String pointCode, long seq) {
        return sha256Hex((deviceCode + ":" + pointCode + ":" + seq)
                .getBytes(StandardCharsets.UTF_8));
    }

    /*
     * 方法功能:幂等键降级口径（ADR-008 D4）——TB 兼容/MQTT 开放通道报文无 seq，
     *   以 deviceCode+pointCode+sampleTime(epochMilli) 为键；HMAC 通道 seq 语义不变
     * 参数:deviceCode-设备编码, pointCode-指标编码, sampleTime-采样时间
     * 返回数据:String-sha256 幂等键
     * */
    public static String dedupKeyWithoutSeq(String deviceCode, String pointCode,
                                            LocalDateTime sampleTime) {
        long epochMilli = sampleTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        return sha256Hex((deviceCode + ":" + pointCode + ":" + epochMilli)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(input));
        } catch (NoSuchAlgorithmException ex) {
            // JDK 规范保证 SHA-256 存在，此处不可能到达
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2] = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }
}
