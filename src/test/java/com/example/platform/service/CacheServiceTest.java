package com.example.platform.service;

import com.example.platform.common.exception.CacheOperationException;
import com.example.platform.config.RedisConfig;
import com.example.platform.service.impl.CacheServiceImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = CacheServiceTest.TestConfig.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class CacheServiceTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({RedisConfig.class, CacheServiceImpl.class})
    static class TestConfig {
    }

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    private static final String PREFIX = "test:cs-" + UUID.randomUUID() + ":";

    @BeforeAll
    static void checkRedisAvailable(@Autowired RedisConnectionFactory factory) {
        boolean available = false;
        try {
            available = "PONG".equals(factory.getConnection().ping());
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(available, "Redis 不可用，跳过集成测试");
    }

    @BeforeEach
    void cleanUp() {
        cacheService.deleteByPattern(PREFIX + "*");
    }

    @AfterEach
    void tearDown() {
        cacheService.deleteByPattern(PREFIX + "*");
    }

    @Test
    @DisplayName("set(key,value) - 存入键值对后可通过get获取")
    void set_withoutTtl_shouldStoreValue() {
        String key = PREFIX + "set:basic";
        cacheService.set(key, "hello");
        String value = cacheService.get(key, String.class);
        assertEquals("hello", value);
    }

    @Test
    @DisplayName("set(key,value,timeout,unit) - 设置1秒过期，等待后get返回null")
    void set_withTtl_shouldExpireAfterTimeout() throws InterruptedException {
        String key = PREFIX + "set:ttl";

        long expireBefore = cacheService.getExpire(key, TimeUnit.MILLISECONDS);
        assertEquals(-2, expireBefore, "键不存在时getExpire应返回-2");

        cacheService.set(key, "temp", 1, TimeUnit.SECONDS);

        long expireAfter = cacheService.getExpire(key, TimeUnit.MILLISECONDS);
        assertTrue(expireAfter > 0, "设置过期后getExpire应返回正数");

        Thread.sleep(1200);

        String value = cacheService.get(key, String.class);
        assertNull(value, "过期后get应返回null");
    }

    @Test
    @DisplayName("get(key,Class) - 键不存在时返回null")
    void get_nonExistentKey_shouldReturnNull() {
        String value = cacheService.get(PREFIX + "get:none", String.class);
        assertNull(value);
    }

    @Test
    @DisplayName("get(key,Class) - 类型不匹配时抛出ClassCastException（clazz.cast()的类型安全检查）")
    void get_typeMismatch_shouldThrowClassCastException() {
        String key = PREFIX + "get:type";
        cacheService.set(key, "not-a-number");
        assertThrows(ClassCastException.class, () -> cacheService.get(key, Integer.class), "存入String后用Integer.class获取应抛出ClassCastException");
    }

    @Test
    @DisplayName("delete(key) - 删除已存在的键返回true")
    void delete_existingKey_shouldReturnTrue() {
        String key = PREFIX + "del:exist";
        cacheService.set(key, "value");
        boolean result = cacheService.delete(key);
        assertTrue(result);
        assertNull(cacheService.get(key, String.class));
    }

    @Test
    @DisplayName("delete(key) - 删除不存在的键也返回true（接口契约：删除操作完成即成功）")
    void delete_nonExistentKey_shouldReturnTrue() {
        boolean result = cacheService.delete(PREFIX + "del:none");
        assertTrue(result, "键不存在时delete也应返回true（接口契约：键不存在也返回true）");
    }

    @Test
    @DisplayName("deleteByPattern(pattern) - 批量删除匹配的键，不匹配的键保留")
    void deleteByPattern_shouldDeleteMatchingKeys() {
        String k1 = PREFIX + "del:1";
        String k2 = PREFIX + "del:2";
        String k3 = PREFIX + "del:3";
        String kOther = PREFIX + "other:keep";

        cacheService.set(k1, "v1");
        cacheService.set(k2, "v2");
        cacheService.set(k3, "v3");
        cacheService.set(kOther, "keep");

        long deleted = cacheService.deleteByPattern(PREFIX + "del:*");
        assertTrue(deleted >= 3, "应至少删除3个匹配的键");

        assertFalse(cacheService.hasKey(k1), "prefix:del:1 应已被删除");
        assertFalse(cacheService.hasKey(k2), "prefix:del:2 应已被删除");
        assertFalse(cacheService.hasKey(k3), "prefix:del:3 应已被删除");
        assertTrue(cacheService.hasKey(kOther), "不匹配的键应保留");
    }

    @Test
    @DisplayName("hasKey(key) - 键存在返回true，不存在返回false")
    void hasKey_shouldReturnCorrectExistence() {
        String key = PREFIX + "has:exist";
        assertFalse(cacheService.hasKey(key), "键不存在时hasKey应返回false");

        cacheService.set(key, "value");
        assertTrue(cacheService.hasKey(key), "键存在时hasKey应返回true");
    }

    @Test
    @DisplayName("setIfAbsent - 首次设置成功返回true，再次设置失败返回false且原值不变")
    void setIfAbsent_shouldSucceedFirstAndFailSecond() {
        String key = PREFIX + "sia:key";

        boolean first = cacheService.setIfAbsent(key, "first", 10, TimeUnit.SECONDS);
        assertTrue(first, "首次setIfAbsent应返回true");

        String valueAfterFirst = cacheService.get(key, String.class);
        assertEquals("first", valueAfterFirst, "首次设置后值应为first");

        boolean second = cacheService.setIfAbsent(key, "second", 10, TimeUnit.SECONDS);
        assertFalse(second, "键已存在时setIfAbsent应返回false");

        String valueAfterSecond = cacheService.get(key, String.class);
        assertEquals("first", valueAfterSecond, "第二次设置失败后原值应不变");
    }

    @Test
    @DisplayName("multiGet/multiSet - 批量写入3个key后批量获取，验证顺序和值正确")
    void multiGetAndSet_shouldReturnValuesInOrder() {
        String k1 = PREFIX + "multi:a";
        String k2 = PREFIX + "multi:b";
        String k3 = PREFIX + "multi:c";

        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, "alpha");
        map.put(k2, "beta");
        map.put(k3, "gamma");
        cacheService.multiSet(map, 30, TimeUnit.SECONDS);

        List<String> keys = List.of(k1, k2, k3);
        Collection<String> values = cacheService.multiGet(keys, String.class);

        List<String> valueList = new ArrayList<>(values);
        assertEquals(3, valueList.size(), "应返回3个值");
        assertEquals("alpha", valueList.get(0), "第1个值应为alpha");
        assertEquals("beta", valueList.get(1), "第2个值应为beta");
        assertEquals("gamma", valueList.get(2), "第3个值应为gamma");
    }

    @Test
    @DisplayName("multiSet(map,timeout,unit) - 批量设置1秒过期，等待后全部为null")
    void multiSet_withTtl_shouldExpireAll() throws InterruptedException {
        String k1 = PREFIX + "multi:ttl:a";
        String k2 = PREFIX + "multi:ttl:b";
        String k3 = PREFIX + "multi:ttl:c";

        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, "x");
        map.put(k2, "y");
        map.put(k3, "z");
        cacheService.multiSet(map, 1, TimeUnit.SECONDS);

        Collection<String> before = cacheService.multiGet(List.of(k1, k2, k3), String.class);
        assertEquals(3, before.size(), "过期前应获取到3个值");

        Thread.sleep(1200);

        Collection<String> after = cacheService.multiGet(List.of(k1, k2, k3), String.class);
        assertTrue(after.stream().allMatch(Objects::isNull), "过期后所有值应为null");
    }

    @Test
    @DisplayName("increment - 不存在的key从0递增，再递增后值正确")
    void increment_shouldStartFromZeroAndIncrease() {
        String key = PREFIX + "incr:key";

        long first = cacheService.increment(key, 5);
        assertEquals(5, first, "不存在的key increment(5) 应返回5");

        long second = cacheService.increment(key, 3);
        assertEquals(8, second, "再次 increment(3) 应返回8");
    }

    @Test
    @DisplayName("decrement - 不存在的key从0递减，再递减后值正确")
    void decrement_shouldStartFromZeroAndDecrease() {
        String key = PREFIX + "decr:key";

        long first = cacheService.decrement(key, 3);
        assertEquals(-3, first, "不存在的key decrement(3) 应返回-3");

        long second = cacheService.decrement(key, 2);
        assertEquals(-5, second, "再次 decrement(2) 应返回-5");
    }

    @Test
    @DisplayName("increment + decrement - 先增后减，验证计数器联动")
    void incrementThenDecrement_shouldWorkCorrectly() {
        String key = PREFIX + "counter";

        cacheService.increment(key, 10);
        long result = cacheService.decrement(key, 4);
        assertEquals(6, result, "increment(10) 后 decrement(4) 应返回6");
    }

    @Test
    @DisplayName("expire - 为已存在的键设置过期时间，返回true")
    void expire_shouldSetTtlOnExistingKey() {
        String key = PREFIX + "expire:key";
        cacheService.set(key, "value");

        long ttlBefore = cacheService.getExpire(key, TimeUnit.SECONDS);
        assertEquals(-1, ttlBefore, "未设置过期时getExpire应返回-1");

        boolean result = cacheService.expire(key, 10, TimeUnit.SECONDS);
        assertTrue(result, "为已存在的键设置过期应返回true");

        long ttlAfter = cacheService.getExpire(key, TimeUnit.SECONDS);
        assertTrue(ttlAfter > 0 && ttlAfter <= 10,
                "设置过期后getExpire应在(0,10]范围内，实际=" + ttlAfter);
    }

    @Test
    @DisplayName("expire - 为不存在的键设置过期时间，返回false")
    void expire_nonExistentKey_shouldReturnFalse() {
        boolean result = cacheService.expire(PREFIX + "expire:none", 10, TimeUnit.SECONDS);
        assertFalse(result, "不存在的键设置过期应返回false");
    }

    @Test
    @DisplayName("getExpire - 键不存在返回-2，无过期返回-1，有过期返回正数")
    void getExpire_shouldReturnCorrectTtl() {
        String keyNonExistent = PREFIX + "ttl:none";
        assertEquals(-2, cacheService.getExpire(keyNonExistent, TimeUnit.MILLISECONDS),
                "不存在的键getExpire应返回-2");

        String keyNoExpire = PREFIX + "ttl:nottl";
        cacheService.set(keyNoExpire, "value");
        assertEquals(-1, cacheService.getExpire(keyNoExpire, TimeUnit.MILLISECONDS),
                "无过期时间的键getExpire应返回-1");

        String keyWithTtl = PREFIX + "ttl:500ms";
        cacheService.set(keyWithTtl, "temp", 500, TimeUnit.MILLISECONDS);
        long ttl = cacheService.getExpire(keyWithTtl, TimeUnit.MILLISECONDS);
        assertTrue(ttl > 0 && ttl <= 500,
                "有过期的键getExpire应返回正数且不超过设定值，实际=" + ttl);
    }

    @Test
    @DisplayName("expire + getExpire - 设置2秒过期后验证剩余时间（允许±200ms误差）")
    void expireAndGetExpire_shouldMatchWithinTolerance() throws InterruptedException {
        String key = PREFIX + "ttl:check";
        cacheService.set(key, "value");
        cacheService.expire(key, 2, TimeUnit.SECONDS);

        Thread.sleep(500);

        long ttl = cacheService.getExpire(key, TimeUnit.MILLISECONDS);
        long expected = 1500;
        long tolerance = 200;
        assertTrue(Math.abs(ttl - expected) <= tolerance,
                "设置2秒过期等待500ms后，剩余时间应约1500ms(±200ms)，实际=" + ttl);
    }

    @Test
    @DisplayName("increment - Redis不可用时应抛出CacheOperationException而非返回0")
    void increment_redisUnavailable_shouldThrowCacheOperationException() {
        org.springframework.data.redis.core.RedisTemplate<String, Object> mockRedisTemplate =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, Object> mockOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);

        org.mockito.Mockito.when(mockRedisTemplate.opsForValue()).thenReturn(mockOps);
        org.mockito.Mockito.when(mockOps.increment("fail:key", 1))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection refused"));

        CacheServiceImpl fallbackService = new CacheServiceImpl(mockRedisTemplate);

        CacheOperationException ex = assertThrows(CacheOperationException.class,
                () -> fallbackService.increment("fail:key", 1),
                "Redis不可用时increment应抛出CacheOperationException");
        assertTrue(ex.getMessage().contains("fail:key"),
                "异常消息应包含失败的key");
    }

    @Test
    @DisplayName("decrement - Redis不可用时应抛出CacheOperationException而非返回0")
    void decrement_redisUnavailable_shouldThrowCacheOperationException() {
        org.springframework.data.redis.core.RedisTemplate<String, Object> mockRedisTemplate =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.RedisTemplate.class);
        org.springframework.data.redis.core.ValueOperations<String, Object> mockOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ValueOperations.class);

        org.mockito.Mockito.when(mockRedisTemplate.opsForValue()).thenReturn(mockOps);
        org.mockito.Mockito.when(mockOps.decrement("fail:key", 1))
                .thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("Connection refused"));

        CacheServiceImpl fallbackService = new CacheServiceImpl(mockRedisTemplate);

        CacheOperationException ex = assertThrows(CacheOperationException.class,
                () -> fallbackService.decrement("fail:key", 1),
                "Redis不可用时decrement应抛出CacheOperationException");
        assertTrue(ex.getMessage().contains("fail:key"),
                "异常消息应包含失败的key");
    }
}
