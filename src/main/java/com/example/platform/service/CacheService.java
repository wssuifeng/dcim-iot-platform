package com.example.platform.service;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/*
 * 类功能:缓存服务接口，封装所有Redis操作，禁止业务层直接使用RedisTemplate
 *
 * 【设计意图】
 * 统一缓存操作入口，好处：
 *   1. 业务层不依赖Redis API细节，未来可无缝切换缓存实现（如Caffeine本地缓存）
 *   2. 集中处理序列化、异常降级、Key格式等横切关注点
 *   3. 方法签名语义化，比RedisTemplate的泛型方法更易用
 *
 * 【Key格式规范】模块:业务:标识
 *   - device:info:1          → 设备信息
 *   - device:type:1          → 设备类型
 *   - alert:unhandled:count  → 未处理告警计数
 *   - dict:metric:temperature → 指标字典
 * */
public interface CacheService {

    /*
     * 类功能:缓存空值占位标记，用于防止缓存穿透
     *
     * 【为什么不能用 "NULL" 字符串？】
     * 序列化器是 Jackson2JsonRedisSerializer + activateDefaultTyping：
     *   - 存 "NULL" 字符串 → 反序列化得到 String → clazz.cast() 转 DictMetric 时 ClassCastException
     *   - 存 NullValue 对象 → 反序列化得到 NullValue → instanceof 判断 → 返回 null
     *
     * 【instanceof 判断的好处】
     *   1. 类型安全：不依赖字符串约定，不会被误判为正常业务数据
     *   2. 编译期检查：NullValue 是具体类型，IDE 能追踪所有使用点
     *   3. 序列化兼容：@class 字段精确标识类型，反序列化不会混淆
     *
     * 【为什么不能是 final class？为什么需要 marker 字段？】
     * RedisConfig 中 ObjectMapper 配置了 DefaultTyping.NON_FINAL，
     *   - final 类型不会嵌入 @class 信息 → 反序列化后丢失类型 → 无法 instanceof 判断
     *   - marker 字段为 Jackson 提供可序列化的属性，否则空类抛出 InvalidDefinitionException
     * */
    static class NullValue implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        @SuppressWarnings("unused")
        private String marker = "__NULL__";
    }

    /*
     * 方法功能:缓存键值对，永不过期（需手动调用expire设置TTL）
     * 参数:key-缓存键, value-缓存值
     * 返回数据:无
     * */
    void set(String key, Object value);

    /*
     * 方法功能:缓存键值对并设置过期时间
     * 参数:key-缓存键, value-缓存值, timeout-过期时长, unit-时间单位
     * 返回数据:无
     * */
    void set(String key, Object value, long timeout, TimeUnit unit);

    /*
     * 方法功能:根据键获取缓存值并转为指定类型
     * 参数:key-缓存键, clazz-目标类型Class对象
     * 返回数据:T-缓存值，键不存在返回null
     * */
    <T> T get(String key, Class<T> clazz);

    /*
     * 方法功能:根据键删除缓存
     * 参数:key-缓存键
     * 返回数据:boolean-是否删除成功（键不存在也返回true）
     * */
    boolean delete(String key);

    /*
     * 方法功能:按模式批量删除缓存键，使用SCAN迭代避免阻塞Redis
     * 参数:pattern-键匹配模式，如 "device:info:*"
     * 返回数据:long-实际删除的键数量
     * */
    long deleteByPattern(String pattern);

    /*
     * 方法功能:判断缓存键是否存在
     * 参数:key-缓存键
     * 返回数据:boolean-键存在返回true
     * */
    boolean hasKey(String key);

    /*
     * 方法功能:原子性设置键值对（仅当键不存在时设置成功），对应Redis的SETNX命令
     * 参数:key-缓存键, value-缓存值, timeout-过期时长, unit-时间单位
     * 返回数据:boolean-设置成功返回true（键之前不存在），键已存在返回false
     * */
    boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit);

    /*
     * 方法功能:批量获取多个键的缓存值并转为指定类型
     * 参数:keys-缓存键集合, clazz-目标类型Class对象
     * 返回数据:Collection<T>-缓存值集合，键不存在的位置为null
     * */
    <T> Collection<T> multiGet(Collection<String> keys, Class<T> clazz);

    /*
     * 方法功能:批量设置多个键值对并统一设置过期时间（非原子操作）
     * 参数:map-键值对映射, timeout-过期时长, unit-时间单位
     * 返回数据:无
     * */
    void multiSet(Map<String, Object> map, long timeout, TimeUnit unit);

    /*
     * 方法功能:递增计数器，键不存在时自动从0开始递增
     * 参数:key-缓存键, delta-递增量（正数）
     * 返回数据:long-递增后的值
     * */
    long increment(String key, long delta);

    /*
     * 方法功能:递减计数器，键不存在时自动从0开始递减
     * 参数:key-缓存键, delta-递减量（正数）
     * 返回数据:long-递减后的值
     * */
    long decrement(String key, long delta);

    /*
     * 方法功能:设置缓存键的过期时间
     * 参数:key-缓存键, timeout-过期时长, unit-时间单位
     * 返回数据:boolean-设置成功返回true，键不存在返回false
     * */
    boolean expire(String key, long timeout, TimeUnit unit);

    /*
     * 方法功能:获取缓存键的剩余过期时间
     * 参数:key-缓存键, unit-时间单位
     * 返回数据:long-剩余时间，键不存在返回-2，键无过期时间返回-1
     * */
    long getExpire(String key, TimeUnit unit);
}
