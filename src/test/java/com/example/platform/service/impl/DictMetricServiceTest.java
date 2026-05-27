package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.platform.config.RedisConfig;
import com.example.platform.entity.DictMetric;
import com.example.platform.mapper.DictMetricMapper;
import com.example.platform.service.CacheService;
import com.example.platform.service.DictMetricService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/*
 * 类功能:指标字典缓存集成测试，验证DictMetricServiceImpl的缓存读写、防穿透、更新删除及刷新逻辑
 *
 * 【测试策略】
 * - 连接真实Redis（Memurai），验证序列化/反序列化端到端正确性
 * - 使用@MockitoBean模拟DictMetricMapper，控制数据库返回值
 * - 测试key使用 dict:metric: 前缀（与实现类一致），通过唯一metricKey值隔离
 * - 每个测试方法前后清理测试key，保证测试隔离
 *
 * 【注意事项】
 * - DictMetricServiceImpl的CACHE_KEY_PREFIX是"dict:metric:"，测试中直接使用该前缀
 * - getFromCache内部调用getOne(wrapper)查DB，通过MockitoBean控制返回值
 * - NullValue占位通过instanceof判断，验证防穿透逻辑
 * - DictMetricServiceImpl有@PostConstruct initCache()，上下文启动时会调用refreshAll()
 *   由于mapper是mock，selectList返回空列表，initCache()不会写入任何缓存
 * */
@SpringBootTest(classes = DictMetricServiceTest.TestConfig.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class DictMetricServiceTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({RedisConfig.class, CacheServiceImpl.class, DictMetricServiceImpl.class})
    static class TestConfig {
    }

    @Autowired
    private CacheService cacheService;

    @Autowired
    private DictMetricService dictMetricService;

    @MockitoBean
    private DictMetricMapper dictMetricMapper;

    private static final String CACHE_KEY_PREFIX = "dict:metric:";

    @BeforeAll
    static void checkRedisAvailable(@Autowired CacheService cacheService) {
        boolean available = false;
        try {
            cacheService.set("test:FR002:ping", "pong", 5, TimeUnit.SECONDS);
            available = "pong".equals(cacheService.get("test:FR002:ping", String.class));
            cacheService.delete("test:FR002:ping");
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(available, "Redis 不可用，跳过集成测试");
    }

    @BeforeEach
    void cleanUp() {
        cacheService.deleteByPattern(CACHE_KEY_PREFIX + "*");
        reset(dictMetricMapper);
    }

    @AfterEach
    void tearDown() {
        cacheService.deleteByPattern(CACHE_KEY_PREFIX + "*");
    }

    /*
     * 方法功能:构造一个测试用的DictMetric对象
     * 参数:id-主键ID, metricKey-指标标识, metricName-指标名称, metricUnit-单位
     * 返回数据:DictMetric-构造好的指标字典对象
     * */
    private DictMetric buildMetric(int id, String metricKey, String metricName, String metricUnit) {
        DictMetric metric = new DictMetric();
        metric.setId(id);
        metric.setMetricKey(metricKey);
        metric.setMetricName(metricName);
        metric.setMetricUnit(metricUnit);
        metric.setDescription("测试指标-" + metricKey);
        return metric;
    }

    /*
     * 方法功能:预先向Redis写入一个有效的DictMetric对象
     * 场景:模拟缓存命中（正常对象）
     * */
    @Test
    @DisplayName("缓存命中(正常对象) - getFromCache返回缓存中的对象，不触发数据库查询")
    void getFromCache_cacheHitWithValidObject_shouldReturnCachedValue() {
        String metricKey = "test-temperature";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;
        DictMetric cachedMetric = buildMetric(1, metricKey, "温度", "℃");

        cacheService.set(cacheKey, cachedMetric, 24, TimeUnit.HOURS);

        DictMetric result = dictMetricService.getFromCache(metricKey);

        assertNotNull(result, "缓存命中时应返回非null对象");
        assertEquals(metricKey, result.getMetricKey(), "返回对象的metricKey应一致");
        assertEquals("温度", result.getMetricName(), "返回对象的metricName应一致");
        assertEquals("℃", result.getMetricUnit(), "返回对象的metricUnit应一致");

        verify(dictMetricMapper, never()).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    /*
     * 方法功能:预先存入NullValue占位，验证getFromCache返回null且不查DB
     * 场景:模拟缓存命中（空值占位，防穿透）
     * */
    @Test
    @DisplayName("缓存命中(NullValue占位) - getFromCache返回null，不触发数据库查询")
    void getFromCache_cacheHitWithNullValue_shouldReturnNullWithoutDbQuery() {
        String metricKey = "test-nonexistent";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;

        cacheService.set(cacheKey, new CacheService.NullValue(), 5, TimeUnit.MINUTES);

        DictMetric result = dictMetricService.getFromCache(metricKey);

        assertNull(result, "NullValue占位命中时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应仍有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "缓存中的值应为NullValue实例");

        verify(dictMetricMapper, never()).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    /*
     * 方法功能:缓存未命中时查DB，DB有数据则回填缓存(TTL=24h)
     * 场景:模拟缓存未命中 → 数据库有数据
     * */
    @Test
    @DisplayName("缓存未命中→DB有数据 - getFromCache查DB后回填缓存，返回DB数据")
    void getFromCache_cacheMissDbHasData_shouldQueryDbAndBackfill() {
        String metricKey = "test-humidity";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;
        DictMetric dbMetric = buildMetric(2, metricKey, "湿度", "%");

        when(dictMetricMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(dbMetric);

        DictMetric result = dictMetricService.getFromCache(metricKey);

        assertNotNull(result, "DB有数据时应返回非null对象");
        assertEquals(metricKey, result.getMetricKey(), "返回对象的metricKey应一致");
        assertEquals("湿度", result.getMetricName(), "返回对象的metricName应一致");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存应已被回填");
        assertTrue(cached instanceof DictMetric, "回填的缓存值应为DictMetric实例");
        DictMetric cachedMetric = (DictMetric) cached;
        assertEquals(metricKey, cachedMetric.getMetricKey(), "回填的metricKey应一致");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 86400, "回填缓存TTL应在(0,86400s]范围内，实际=" + ttl + "s");

        verify(dictMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    /*
     * 方法功能:缓存未命中且DB也无数据时，存入NullValue占位(TTL=5min)
     * 场景:模拟缓存穿透防护
     * */
    @Test
    @DisplayName("缓存未命中→DB无数据 - getFromCache返回null，缓存存入NullValue占位(TTL=5min)")
    void getFromCache_cacheMissDbNoData_shouldStoreNullValuePlaceholder() {
        String metricKey = "test-ghost-metric";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;

        when(dictMetricMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        DictMetric result = dictMetricService.getFromCache(metricKey);

        assertNull(result, "DB无数据时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "占位值应为NullValue实例");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.MINUTES);
        assertTrue(ttl > 0 && ttl <= 5, "NullValue占位的TTL应在(0,5min]范围内，实际=" + ttl + "min");

        verify(dictMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    /*
     * 方法功能:NullValue占位过期后，再次请求应重新查DB
     * 场景:验证NullValue占位的短TTL机制——过期后缓存miss，重新走DB查询
     * */
    @Test
    @DisplayName("NullValue占位过期→DB仍无数据 - 占位过期后重新查DB，仍无数据则再次存入NullValue")
    void getFromCache_nullValueExpiredDbStillNoData_shouldQueryDbAgain() throws InterruptedException {
        String metricKey = "test-expired-null";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;

        cacheService.set(cacheKey, new CacheService.NullValue(), 1, TimeUnit.SECONDS);

        Thread.sleep(1200);

        when(dictMetricMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(null);

        DictMetric result = dictMetricService.getFromCache(metricKey);

        assertNull(result, "占位过期后重新查DB，DB仍无数据应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "应重新存入NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "重新存入的占位应为NullValue实例");

        verify(dictMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    /*
     * 方法功能:NullValue占位过期后DB已有数据，应查DB回填有效对象
     * 场景:验证NullValue短TTL的设计意图——DB后续写入了数据，占位过期后可及时感知
     * */
    @Test
    @DisplayName("NullValue占位过期→DB已有数据 - 占位过期后查DB回填有效对象(TTL=24h)")
    void getFromCache_nullValueExpiredDbNowHasData_shouldQueryDbAndBackfill() throws InterruptedException {
        String metricKey = "test-expired-then-data";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;
        DictMetric newMetric = buildMetric(3, metricKey, "新写入指标", "kW");

        cacheService.set(cacheKey, new CacheService.NullValue(), 1, TimeUnit.SECONDS);

        Thread.sleep(1200);

        when(dictMetricMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(newMetric);

        DictMetric result = dictMetricService.getFromCache(metricKey);

        assertNotNull(result, "占位过期后DB有数据应返回有效对象");
        assertEquals(metricKey, result.getMetricKey(), "返回的metricKey应一致");
        assertEquals("新写入指标", result.getMetricName(), "返回的metricName应一致");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存应已被回填");
        assertTrue(cached instanceof DictMetric, "回填值应为DictMetric实例（非NullValue）");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 86400, "回填缓存TTL应在(0,86400s]范围内");

        verify(dictMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }

    /*
     * 方法功能:验证save操作后对应key的缓存已被删除
     * 场景:先写缓存，再调用save，验证缓存被清除（先写DB再删缓存策略）
     * */
    @Test
    @DisplayName("保存操作 - save后对应key的缓存被删除")
    void save_shouldEvictCache() {
        String metricKey = "test-save-metric";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;
        DictMetric existing = buildMetric(4, metricKey, "已有指标", "V");

        cacheService.set(cacheKey, existing, 24, TimeUnit.HOURS);
        assertTrue(cacheService.hasKey(cacheKey), "保存前缓存应存在");

        DictMetric newEntity = buildMetric(5, metricKey, "新增指标", "A");

        when(dictMetricMapper.insert(any(DictMetric.class))).thenReturn(1);

        boolean result = dictMetricService.save(newEntity);

        assertTrue(result, "save应返回true");
        assertFalse(cacheService.hasKey(cacheKey), "保存后缓存应被删除");
    }

    /*
     * 方法功能:验证更新操作后对应key的缓存已被删除
     * 场景:先写缓存，再调用updateById，验证缓存被清除
     * */
    @Test
    @DisplayName("更新操作 - updateById后对应key的缓存被删除")
    void updateById_shouldEvictCache() {
        String metricKey = "test-update-metric";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;
        DictMetric original = buildMetric(6, metricKey, "原始名称", "℃");

        cacheService.set(cacheKey, original, 24, TimeUnit.HOURS);
        assertTrue(cacheService.hasKey(cacheKey), "更新前缓存应存在");

        DictMetric updated = buildMetric(6, metricKey, "更新名称", "℉");

        when(dictMetricMapper.updateById(any(DictMetric.class))).thenReturn(1);

        dictMetricService.updateById(updated);

        assertFalse(cacheService.hasKey(cacheKey), "更新后缓存应被删除");
    }

    /*
     * 方法功能:验证删除操作后对应key的缓存已被删除
     * 场景:先写缓存，再调用removeById，验证缓存被清除
     * */
    @Test
    @DisplayName("删除操作 - removeById后对应key的缓存被删除")
    void removeById_shouldEvictCache() {
        String metricKey = "test-delete-metric";
        String cacheKey = CACHE_KEY_PREFIX + metricKey;
        DictMetric existing = buildMetric(7, metricKey, "待删除指标", "Pa");

        cacheService.set(cacheKey, existing, 24, TimeUnit.HOURS);
        assertTrue(cacheService.hasKey(cacheKey), "删除前缓存应存在");

        when(dictMetricMapper.selectById(existing.getId())).thenReturn(existing);
        when(dictMetricMapper.deleteById(existing.getId())).thenReturn(1);

        dictMetricService.removeById(existing.getId());

        assertFalse(cacheService.hasKey(cacheKey), "删除后缓存应被删除");
    }

    /*
     * 方法功能:验证手动刷新后所有dict:metric:*的key被清除，DB数据被重新回填
     * 场景:先写入一些缓存（含过期数据），调用refreshCache后验证全量回填
     * */
    @Test
    @DisplayName("手动刷新 - refreshCache清除所有dict:metric:*缓存后全量回填DB数据")
    void refreshCache_shouldClearAllAndBackfillFromDb() {
        String staleKey = "test-stale-metric";
        String cacheKey = CACHE_KEY_PREFIX + staleKey;
        DictMetric staleMetric = buildMetric(8, staleKey, "过期名称", "旧单位");
        cacheService.set(cacheKey, staleMetric, 24, TimeUnit.HOURS);
        assertTrue(cacheService.hasKey(cacheKey), "刷新前过期缓存应存在");

        DictMetric metric1 = buildMetric(9, "test-refresh-1", "刷新指标1", "m/s");
        DictMetric metric2 = buildMetric(10, "test-refresh-2", "刷新指标2", "kg");
        when(dictMetricMapper.selectList(any())).thenReturn(List.of(metric1, metric2));

        dictMetricService.refreshCache();

        assertFalse(cacheService.hasKey(cacheKey), "刷新后旧缓存应被清除");

        Object refreshed1 = cacheService.get(CACHE_KEY_PREFIX + "test-refresh-1", Object.class);
        Object refreshed2 = cacheService.get(CACHE_KEY_PREFIX + "test-refresh-2", Object.class);

        assertNotNull(refreshed1, "刷新后test-refresh-1应被回填");
        assertNotNull(refreshed2, "刷新后test-refresh-2应被回填");
        assertTrue(refreshed1 instanceof DictMetric, "回填值应为DictMetric实例");
        assertTrue(refreshed2 instanceof DictMetric, "回填值应为DictMetric实例");

        DictMetric rm1 = (DictMetric) refreshed1;
        DictMetric rm2 = (DictMetric) refreshed2;
        assertEquals("刷新指标1", rm1.getMetricName(), "回填的metricName应一致");
        assertEquals("刷新指标2", rm2.getMetricName(), "回填的metricName应一致");

        long ttl1 = cacheService.getExpire(CACHE_KEY_PREFIX + "test-refresh-1", TimeUnit.SECONDS);
        assertTrue(ttl1 > 0 && ttl1 <= 86400, "回填缓存TTL应在(0,86400s]范围内");

        verify(dictMetricMapper, atLeastOnce()).selectList(any());
    }

    /*
     * 方法功能:验证连续两次getFromCache，第一次miss查DB回填，第二次hit不查DB
     * 场景:验证Cache-Aside模式的完整读流程
     * */
    @Test
    @DisplayName("连续读取 - 第一次miss查DB回填，第二次hit直接返回缓存")
    void getFromCache_twiceRead_shouldHitCacheOnSecondCall() {
        String metricKey = "test-twice-read";
        DictMetric dbMetric = buildMetric(11, metricKey, "连续读取指标", "W");

        when(dictMetricMapper.selectOne(any(LambdaQueryWrapper.class), anyBoolean())).thenReturn(dbMetric);

        DictMetric first = dictMetricService.getFromCache(metricKey);
        assertNotNull(first, "第一次读取应返回DB数据");
        verify(dictMetricMapper, times(1)).selectOne(any(LambdaQueryWrapper.class), anyBoolean());

        reset(dictMetricMapper);

        DictMetric second = dictMetricService.getFromCache(metricKey);
        assertNotNull(second, "第二次读取应返回缓存数据");
        assertEquals(metricKey, second.getMetricKey(), "第二次返回的metricKey应一致");
        verify(dictMetricMapper, never()).selectOne(any(LambdaQueryWrapper.class), anyBoolean());
    }
}
