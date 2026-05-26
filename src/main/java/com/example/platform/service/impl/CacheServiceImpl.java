package com.example.platform.service.impl;

import com.example.platform.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*
 * 类功能:缓存服务实现类，封装RedisTemplate的所有操作，提供统一的缓存访问接口
 *
 * 【降级策略】
 * Redis异常均捕获并记录日志，不向业务层抛出：
 *   - 读操作（get/multiGet/hasKey等）：返回null/false，业务层走数据库
 *   - 写操作（set/delete/increment等）：仅记录日志，不影响业务主流程
 * 这样即使Redis宕机，系统仍可降级运行，只是性能退化为全量查库
 *
 * 【类型安全——ClassCastException是故意传播的】
 * get/multiGet在类型不匹配时抛出ClassCastException，这不是降级范围内的Redis异常：
 *   - 类型不匹配是调用方编程错误（传入了错误的Class参数），不应被静默吞掉
 *   - 早期暴露错误比让错误数据在业务层流转更安全
 *   - GlobalExceptionHandler有专用处理器将其映射为HTTP 400
 * */
@Service
public class CacheServiceImpl implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(CacheServiceImpl.class);

    private final RedisTemplate<String, Object> redisTemplate;

    public CacheServiceImpl(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /*
     * 方法功能:缓存键值对，永不过期（需手动调用expire设置TTL）
     * 参数:key-缓存键, value-缓存值
     * 返回数据:无
     * */
    @Override
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis写入失败, key={}", key, e);
        }
    }

    /*
     * 方法功能:缓存键值对并设置过期时间
     * 参数:key-缓存键, value-缓存值, timeout-过期时长, unit-时间单位
     * 返回数据:无
     * */
    @Override
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis写入失败, key={}, timeout={}{}", key, timeout, unit, e);
        }
    }

    /*
     * 方法功能:根据键获取缓存值并转为指定类型
     * 参数:key-缓存键, clazz-目标类型Class对象
     * 返回数据:T-缓存值，键不存在或Redis异常返回null
     * 异常:类型不匹配时抛出ClassCastException（调用方编程错误，由GlobalExceptionHandler处理为400）
     *
     * 【为什么保留Class参数？】
     * Jackson2JsonRedisSerializer虽然通过@class自动还原类型，
     * 但返回的是Object引用，编译期无类型约束。保留Class参数并clazz.cast()：
     *   1. 调用方显式声明期望类型，代码意图清晰
     *   2. 类型不匹配时在缓存层就抛ClassCastException，GlobalExceptionHandler统一处理
     *   3. 未来切换为StringRedisSerializer+手动JSON时，接口无需改动
     * */
    @Override
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            return clazz.cast(value);
        } catch (ClassCastException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis读取失败, key={}", key, e);
            return null;
        }
    }

    /*
     * 方法功能:根据键删除缓存
     * 参数:key-缓存键
     * 返回数据:boolean-是否删除成功（键不存在也返回true）
     * */
    @Override
    public boolean delete(String key) {
        try {
            redisTemplate.delete(key);
            return true;
        } catch (Exception e) {
            log.warn("Redis删除失败, key={}", key, e);
            return false;
        }
    }

    /*
     * 方法功能:按模式批量删除缓存键，使用SCAN迭代避免阻塞Redis
     * 参数:pattern-键匹配模式，如 "device:info:*"
     * 返回数据:long-实际删除的键数量
     *
     * 【为什么严禁KEYS命令？】
     * KEYS pattern 是O(N)全库扫描，会阻塞Redis单线程：
     *   - 100万个Key时，KEYS * 可能耗时数秒
     *   - 期间所有其他客户端请求全部排队等待
     *   - 生产环境使用KEYS等同于制造一次Redis宕机
     *
     * SCAN命令的原理：
     *   - 基于游标迭代，每次调用返回一小批结果+下一个游标位置
     *   - 不阻塞Redis，每次只处理少量Key
     *   - 不保证不重复（需调用方去重），但删除场景可容忍
     * */
    @Override
    public long deleteByPattern(String pattern) {
        try {
            Set<String> keys = new HashSet<>();
            ScanOptions scanOptions = ScanOptions.scanOptions().match(pattern).count(100).build();
            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (keys.isEmpty()) {
                return 0;
            }
            Long deleted = redisTemplate.delete(keys);
            return deleted != null ? deleted : 0;
        } catch (Exception e) {
            log.warn("Redis按模式删除失败, pattern={}", pattern, e);
            return 0;
        }
    }

    /*
     * 方法功能:判断缓存键是否存在
     * 参数:key-缓存键
     * 返回数据:boolean-键存在返回true
     * */
    @Override
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis判断键存在失败, key={}", key, e);
            return false;
        }
    }

    /*
     * 方法功能:原子性设置键值对（仅当键不存在时设置成功），对应Redis的SETNX命令
     * 参数:key-缓存键, value-缓存值, timeout-过期时长, unit-时间单位
     * 返回数据:boolean-设置成功返回true（键之前不存在），键已存在返回false
     *
     * 【SETNX的应用场景】
     *   1. 幂等控制：setIfAbsent("order:123:submit", "1", 10s) 防止重复提交
     *   2. 分布式锁：setIfAbsent("lock:device:1", "instance-1", 30s) 获取锁
     *   3. 限流计数：配合increment实现滑动窗口限流
     * */
    @Override
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit)
            );
        } catch (Exception e) {
            log.warn("Redis SETNX失败, key={}", key, e);
            return false;
        }
    }

    /*
     * 方法功能:批量获取多个键的缓存值并转为指定类型
     * 参数:keys-缓存键集合, clazz-目标类型Class对象
     * 返回数据:Collection<T>-缓存值集合，键不存在的位置为null，Redis异常返回空集合
     * 异常:类型不匹配时抛出ClassCastException（同get方法）
     * */
    @Override
    public <T> Collection<T> multiGet(Collection<String> keys, Class<T> clazz) {
        try {
            List<Object> rawValues = redisTemplate.opsForValue().multiGet(keys);
            if (rawValues == null) {
                return Collections.emptyList();
            }
            List<T> result = new ArrayList<>(rawValues.size());
            for (Object raw : rawValues) {
                result.add(raw != null ? clazz.cast(raw) : null);
            }
            return result;
        } catch (ClassCastException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis批量读取失败, keys={}", keys, e);
            return Collections.emptyList();
        }
    }

    /*
     * 方法功能:批量设置多个键值对并统一设置过期时间（非原子操作）
     * 参数:map-键值对映射, timeout-过期时长, unit-时间单位
     * 返回数据:无
     *
     * 【为什么非原子？】
     * Pipeline将多条命令打包一次发送，减少网络往返，但每条命令独立执行：
     *   - 中间某条SETEX失败不影响其他
     *   - 不保证"全部成功或全部失败"
     * 如需原子性，应使用Lua脚本（Redis单线程执行Lua天然原子），但大多数缓存场景
     * 不需要强原子性，Pipeline的性能优势更实际
     * */
    @Override
    @SuppressWarnings("unchecked")
    public void multiSet(Map<String, Object> map, long timeout, TimeUnit unit) {
        try {
            RedisCallback<Void> callback = (connection) -> {
                RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    byte[] keyBytes = redisTemplate.getStringSerializer().serialize(entry.getKey());
                    byte[] valueBytes = valueSerializer.serialize(entry.getValue());
                    if (keyBytes != null && valueBytes != null) {
                        connection.setEx(keyBytes, unit.toSeconds(timeout), valueBytes);
                    }
                }
                return null;
            };
            redisTemplate.executePipelined(callback);
        } catch (Exception e) {
            log.warn("Redis批量写入失败, keyCount={}", map.size(), e);
        }
    }

    /*
     * 方法功能:递增计数器，键不存在时自动从0开始递增
     * 参数:key-缓存键, delta-递增量（正数）
     * 返回数据:long-递增后的值
     * */
    @Override
    public long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.warn("Redis递增失败, key={}, delta={}", key, delta, e);
            return 0;
        }
    }

    /*
     * 方法功能:递减计数器，键不存在时自动从0开始递减
     * 参数:key-缓存键, delta-递减量（正数）
     * 返回数据:long-递减后的值
     * */
    @Override
    public long decrement(String key, long delta) {
        try {
            return redisTemplate.opsForValue().decrement(key, delta);
        } catch (Exception e) {
            log.warn("Redis递减失败, key={}, delta={}", key, delta, e);
            return 0;
        }
    }

    /*
     * 方法功能:设置缓存键的过期时间
     * 参数:key-缓存键, timeout-过期时长, unit-时间单位
     * 返回数据:boolean-设置成功返回true，键不存在返回false
     * */
    @Override
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.warn("Redis设置过期时间失败, key={}", key, e);
            return false;
        }
    }

    /*
     * 方法功能:获取缓存键的剩余过期时间
     * 参数:key-缓存键, unit-时间单位
     * 返回数据:long-剩余时间，键不存在返回-2，键无过期时间返回-1
     * */
    @Override
    public long getExpire(String key, TimeUnit unit) {
        try {
            Long ttl = redisTemplate.getExpire(key, unit);
            return ttl != null ? ttl : -2;
        } catch (Exception e) {
            log.warn("Redis获取过期时间失败, key={}", key, e);
            return -2;
        }
    }
}
