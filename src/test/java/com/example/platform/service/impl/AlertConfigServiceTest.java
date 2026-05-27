package com.example.platform.service.impl;

import com.example.platform.config.RedisConfig;
import com.example.platform.dto.AlertRuleConfig;
import com.example.platform.dto.AlertRuleDefinition;
import com.example.platform.entity.Device;
import com.example.platform.entity.DeviceType;
import com.example.platform.service.AlertConfigService;
import com.example.platform.service.CacheService;
import com.example.platform.service.DeviceService;
import com.example.platform.service.DeviceTypeService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * 类功能:告警配置缓存集成测试，验证AlertConfigServiceImpl的设备级/类型级缓存读写、防穿透及失效逻辑
 *
 * 【测试策略】
 * - 连接真实Redis（Memurai），验证序列化/反序列化端到端正确性
 * - 使用@MockitoBean模拟DeviceService和DeviceTypeService，控制数据库返回值
 * - 测试key使用 config:alert:device: 和 config:alert:type: 前缀（与实现类一致）
 * - 每个测试方法前后清理测试key，保证测试隔离
 *
 * 【缓存Key规范】
 * - 设备级: config:alert:device:{deviceId}，TTL=1h
 * - 类型级: config:alert:type:{typeId}，TTL=1h
 * - NullValue占位: 同上，TTL=5min
 *
 * 【AlertConfigServiceImpl无@PostConstruct】
 * 与DictMetricServiceImpl不同，AlertConfigServiceImpl没有初始化缓存加载逻辑，
 * 上下文启动时不会主动查询DB，因此无需担心mock未配置时的NPE问题
 * */
@SpringBootTest(classes = AlertConfigServiceTest.TestConfig.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
class AlertConfigServiceTest {

    @Configuration
    @EnableAutoConfiguration
    @Import({RedisConfig.class, CacheServiceImpl.class, AlertConfigServiceImpl.class})
    static class TestConfig {
    }

    @Autowired
    private CacheService cacheService;

    @Autowired
    private AlertConfigService alertConfigService;

    @MockitoBean
    private DeviceService deviceService;

    @MockitoBean
    private DeviceTypeService deviceTypeService;

    private static final String DEVICE_KEY_PREFIX = "config:alert:device:";
    private static final String TYPE_KEY_PREFIX = "config:alert:type:";

    @BeforeAll
    static void checkRedisAvailable(@Autowired CacheService cacheService) {
        boolean available = false;
        try {
            cacheService.set("test:FR004:ping", "pong", 5, TimeUnit.SECONDS);
            available = "pong".equals(cacheService.get("test:FR004:ping", String.class));
            cacheService.delete("test:FR004:ping");
        } catch (Exception ignored) {
        }
        Assumptions.assumeTrue(available, "Redis 不可用，跳过集成测试");
    }

    @BeforeEach
    void cleanUp() {
        cacheService.deleteByPattern(DEVICE_KEY_PREFIX + "*");
        cacheService.deleteByPattern(TYPE_KEY_PREFIX + "*");
        reset(deviceService, deviceTypeService);
    }

    @AfterEach
    void tearDown() {
        cacheService.deleteByPattern(DEVICE_KEY_PREFIX + "*");
        cacheService.deleteByPattern(TYPE_KEY_PREFIX + "*");
    }

    /*
     * 方法功能:构造一个测试用的AlertRuleConfig对象
     * 参数:metricKey-指标标识, threshold-阈值, level-告警级别
     * 返回数据:AlertRuleConfig-构造好的告警规则配置
     * */
    private AlertRuleConfig buildAlertConfig(String metricKey, double threshold, int level) {
        AlertRuleDefinition rule = new AlertRuleDefinition();
        rule.setMetricKey(metricKey);
        rule.setOperator("GT");
        rule.setThreshold(threshold);
        rule.setLevel(level);
        rule.setMessageTemplate(metricKey + "当前值{value}超过阈值{threshold}");

        AlertRuleConfig config = new AlertRuleConfig();
        config.setRules(List.of(rule));
        return config;
    }

    /*
     * 方法功能:构造一个测试用的Device对象
     * 参数:deviceId-设备ID, typeId-类型ID, config-告警配置(可为null)
     * 返回数据:Device-构造好的设备对象
     * */
    private Device buildDevice(Integer deviceId, Integer typeId, AlertRuleConfig config) {
        Device device = new Device();
        device.setId(deviceId);
        device.setDeviceName("测试设备-" + deviceId);
        device.setDeviceCode("TEST-DEV-" + deviceId);
        device.setTypeId(typeId);
        device.setStatus(1);
        device.setLocation("测试位置");
        device.setAlertRuleConfig(config);
        return device;
    }

    /*
     * 方法功能:构造一个测试用的DeviceType对象
     * 参数:typeId-类型ID, config-告警配置(可为null)
     * 返回数据:DeviceType-构造好的设备类型对象
     * */
    private DeviceType buildDeviceType(Integer typeId, AlertRuleConfig config) {
        DeviceType type = new DeviceType();
        type.setId(typeId);
        type.setTypeName("测试类型-" + typeId);
        type.setTypeCode("TEST-TYPE-" + typeId);
        type.setAlertRuleConfig(config);
        return type;
    }

    // ==================== 设备级缓存测试 ====================

    @Test
    @DisplayName("设备级-缓存命中(正常对象) - 返回缓存中的配置，不查DB")
    void getDeviceAlertConfig_cacheHitWithValidObject_shouldReturnCachedValue() {
        Integer deviceId = 1001;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;
        AlertRuleConfig cachedConfig = buildAlertConfig("temperature", 80.0, 2);

        cacheService.set(cacheKey, cachedConfig, 1, TimeUnit.HOURS);

        AlertRuleConfig result = alertConfigService.getDeviceAlertConfig(deviceId);

        assertNotNull(result, "缓存命中时应返回非null配置");
        assertNotNull(result.getRules(), "配置中的规则列表不应为null");
        assertEquals(1, result.getRules().size(), "规则列表应包含1条规则");
        assertEquals("temperature", result.getRules().get(0).getMetricKey(), "规则metricKey应一致");
        assertEquals(80.0, result.getRules().get(0).getThreshold(), 0.001, "规则阈值应一致");

        verify(deviceService, never()).getById(anyInt());
    }

    @Test
    @DisplayName("设备级-缓存命中(NullValue占位) - 返回null，不查DB")
    void getDeviceAlertConfig_cacheHitWithNullValue_shouldReturnNullWithoutDbQuery() {
        Integer deviceId = 1002;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;

        cacheService.set(cacheKey, new CacheService.NullValue(), 5, TimeUnit.MINUTES);

        AlertRuleConfig result = alertConfigService.getDeviceAlertConfig(deviceId);

        assertNull(result, "NullValue占位命中时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应仍有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "缓存值应为NullValue实例");

        verify(deviceService, never()).getById(anyInt());
    }

    @Test
    @DisplayName("设备级-缓存未命中→DB有配置 - 查DB后回填缓存(TTL=1h)")
    void getDeviceAlertConfig_cacheMissDbHasConfig_shouldQueryDbAndBackfill() {
        Integer deviceId = 1003;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;
        AlertRuleConfig dbConfig = buildAlertConfig("humidity", 90.0, 3);
        Device device = buildDevice(deviceId, 10, dbConfig);

        when(deviceService.getById(deviceId)).thenReturn(device);

        AlertRuleConfig result = alertConfigService.getDeviceAlertConfig(deviceId);

        assertNotNull(result, "DB有配置时应返回非null");
        assertEquals("humidity", result.getRules().get(0).getMetricKey(), "配置metricKey应一致");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存应已被回填");
        assertTrue(cached instanceof AlertRuleConfig, "回填值应为AlertRuleConfig实例");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 3600, "回填缓存TTL应在(0,3600s]范围内，实际=" + ttl + "s");

        verify(deviceService, times(1)).getById(deviceId);
    }

    @Test
    @DisplayName("设备级-缓存未命中→DB无配置(设备存在) - 存入NullValue占位(TTL=5min)")
    void getDeviceAlertConfig_cacheMissDbNoConfig_shouldStoreNullValuePlaceholder() {
        Integer deviceId = 1004;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;
        Device device = buildDevice(deviceId, 10, null);

        when(deviceService.getById(deviceId)).thenReturn(device);

        AlertRuleConfig result = alertConfigService.getDeviceAlertConfig(deviceId);

        assertNull(result, "DB无配置时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "占位值应为NullValue实例");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.MINUTES);
        assertTrue(ttl > 0 && ttl <= 5, "NullValue占位TTL应在(0,5min]范围内，实际=" + ttl + "min");

        verify(deviceService, times(1)).getById(deviceId);
    }

    @Test
    @DisplayName("设备级-缓存未命中→DB无此设备 - 存入NullValue占位(TTL=5min)")
    void getDeviceAlertConfig_cacheMissDeviceNotExist_shouldStoreNullValuePlaceholder() {
        Integer deviceId = 1005;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;

        when(deviceService.getById(deviceId)).thenReturn(null);

        AlertRuleConfig result = alertConfigService.getDeviceAlertConfig(deviceId);

        assertNull(result, "设备不存在时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "占位值应为NullValue实例");

        verify(deviceService, times(1)).getById(deviceId);
    }

    @Test
    @DisplayName("设备级-NullValue占位过期→DB已有配置 - 占位过期后查DB回填有效配置(TTL=1h)")
    void getDeviceAlertConfig_nullValueExpiredDbNowHasConfig_shouldQueryDbAndBackfill() throws InterruptedException {
        Integer deviceId = 1006;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;
        AlertRuleConfig newConfig = buildAlertConfig("temperature", 85.0, 2);
        Device device = buildDevice(deviceId, 10, newConfig);

        cacheService.set(cacheKey, new CacheService.NullValue(), 1, TimeUnit.SECONDS);

        Thread.sleep(1200);

        when(deviceService.getById(deviceId)).thenReturn(device);

        AlertRuleConfig result = alertConfigService.getDeviceAlertConfig(deviceId);

        assertNotNull(result, "占位过期后DB有配置应返回有效对象");
        assertEquals("temperature", result.getRules().get(0).getMetricKey(), "配置metricKey应一致");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存应已被回填");
        assertTrue(cached instanceof AlertRuleConfig, "回填值应为AlertRuleConfig实例（非NullValue）");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 3600, "回填缓存TTL应在(0,3600s]范围内");

        verify(deviceService, times(1)).getById(deviceId);
    }

    // ==================== 类型级缓存测试 ====================

    @Test
    @DisplayName("类型级-缓存命中(正常对象) - 返回缓存中的配置，不查DB")
    void getTypeAlertConfig_cacheHitWithValidObject_shouldReturnCachedValue() {
        Integer typeId = 2001;
        String cacheKey = TYPE_KEY_PREFIX + typeId;
        AlertRuleConfig cachedConfig = buildAlertConfig("pressure", 100.0, 1);

        cacheService.set(cacheKey, cachedConfig, 1, TimeUnit.HOURS);

        AlertRuleConfig result = alertConfigService.getTypeAlertConfig(typeId);

        assertNotNull(result, "缓存命中时应返回非null配置");
        assertEquals("pressure", result.getRules().get(0).getMetricKey(), "配置metricKey应一致");
        assertEquals(100.0, result.getRules().get(0).getThreshold(), 0.001, "配置阈值应一致");

        verify(deviceTypeService, never()).getById(anyInt());
    }

    @Test
    @DisplayName("类型级-缓存命中(NullValue占位) - 返回null，不查DB")
    void getTypeAlertConfig_cacheHitWithNullValue_shouldReturnNullWithoutDbQuery() {
        Integer typeId = 2002;
        String cacheKey = TYPE_KEY_PREFIX + typeId;

        cacheService.set(cacheKey, new CacheService.NullValue(), 5, TimeUnit.MINUTES);

        AlertRuleConfig result = alertConfigService.getTypeAlertConfig(typeId);

        assertNull(result, "NullValue占位命中时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应仍有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "缓存值应为NullValue实例");

        verify(deviceTypeService, never()).getById(anyInt());
    }

    @Test
    @DisplayName("类型级-缓存未命中→DB有配置 - 查DB后回填缓存(TTL=1h)")
    void getTypeAlertConfig_cacheMissDbHasConfig_shouldQueryDbAndBackfill() {
        Integer typeId = 2003;
        String cacheKey = TYPE_KEY_PREFIX + typeId;
        AlertRuleConfig dbConfig = buildAlertConfig("voltage", 220.0, 2);
        DeviceType deviceType = buildDeviceType(typeId, dbConfig);

        when(deviceTypeService.getById(typeId)).thenReturn(deviceType);

        AlertRuleConfig result = alertConfigService.getTypeAlertConfig(typeId);

        assertNotNull(result, "DB有配置时应返回非null");
        assertEquals("voltage", result.getRules().get(0).getMetricKey(), "配置metricKey应一致");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存应已被回填");
        assertTrue(cached instanceof AlertRuleConfig, "回填值应为AlertRuleConfig实例");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 3600, "回填缓存TTL应在(0,3600s]范围内，实际=" + ttl + "s");

        verify(deviceTypeService, times(1)).getById(typeId);
    }

    @Test
    @DisplayName("类型级-缓存未命中→DB无配置(类型存在) - 存入NullValue占位(TTL=5min)")
    void getTypeAlertConfig_cacheMissDbNoConfig_shouldStoreNullValuePlaceholder() {
        Integer typeId = 2004;
        String cacheKey = TYPE_KEY_PREFIX + typeId;
        DeviceType deviceType = buildDeviceType(typeId, null);

        when(deviceTypeService.getById(typeId)).thenReturn(deviceType);

        AlertRuleConfig result = alertConfigService.getTypeAlertConfig(typeId);

        assertNull(result, "DB无配置时应返回null");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存中应有NullValue占位");
        assertTrue(cached instanceof CacheService.NullValue, "占位值应为NullValue实例");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.MINUTES);
        assertTrue(ttl > 0 && ttl <= 5, "NullValue占位TTL应在(0,5min]范围内，实际=" + ttl + "min");

        verify(deviceTypeService, times(1)).getById(typeId);
    }

    @Test
    @DisplayName("类型级-NullValue占位过期→DB已有配置 - 占位过期后查DB回填有效配置(TTL=1h)")
    void getTypeAlertConfig_nullValueExpiredDbNowHasConfig_shouldQueryDbAndBackfill() throws InterruptedException {
        Integer typeId = 2005;
        String cacheKey = TYPE_KEY_PREFIX + typeId;
        AlertRuleConfig newConfig = buildAlertConfig("voltage", 240.0, 1);
        DeviceType deviceType = buildDeviceType(typeId, newConfig);

        cacheService.set(cacheKey, new CacheService.NullValue(), 1, TimeUnit.SECONDS);

        Thread.sleep(1200);

        when(deviceTypeService.getById(typeId)).thenReturn(deviceType);

        AlertRuleConfig result = alertConfigService.getTypeAlertConfig(typeId);

        assertNotNull(result, "占位过期后DB有配置应返回有效对象");
        assertEquals("voltage", result.getRules().get(0).getMetricKey(), "配置metricKey应一致");

        Object cached = cacheService.get(cacheKey, Object.class);
        assertNotNull(cached, "缓存应已被回填");
        assertTrue(cached instanceof AlertRuleConfig, "回填值应为AlertRuleConfig实例（非NullValue）");

        long ttl = cacheService.getExpire(cacheKey, TimeUnit.SECONDS);
        assertTrue(ttl > 0 && ttl <= 3600, "回填缓存TTL应在(0,3600s]范围内");

        verify(deviceTypeService, times(1)).getById(typeId);
    }

    // ==================== 缓存失效测试 ====================

    @Test
    @DisplayName("设备级缓存失效 - evictDeviceAlertConfig删除对应key")
    void evictDeviceAlertConfig_shouldDeleteDeviceCacheKey() {
        Integer deviceId = 3001;
        String cacheKey = DEVICE_KEY_PREFIX + deviceId;
        AlertRuleConfig config = buildAlertConfig("temperature", 80.0, 2);

        cacheService.set(cacheKey, config, 1, TimeUnit.HOURS);
        assertTrue(cacheService.hasKey(cacheKey), "失效前缓存应存在");

        alertConfigService.evictDeviceAlertConfig(deviceId);

        assertFalse(cacheService.hasKey(cacheKey), "失效后缓存应被删除");
    }

    @Test
    @DisplayName("类型级缓存失效 - evictTypeAlertConfig删除对应key")
    void evictTypeAlertConfig_shouldDeleteTypeCacheKey() {
        Integer typeId = 3002;
        String cacheKey = TYPE_KEY_PREFIX + typeId;
        AlertRuleConfig config = buildAlertConfig("pressure", 100.0, 1);

        cacheService.set(cacheKey, config, 1, TimeUnit.HOURS);
        assertTrue(cacheService.hasKey(cacheKey), "失效前缓存应存在");

        alertConfigService.evictTypeAlertConfig(typeId);

        assertFalse(cacheService.hasKey(cacheKey), "失效后缓存应被删除");
    }

    @Test
    @DisplayName("设备级缓存失效不影响类型级缓存 - 两级缓存独立管理")
    void evictDeviceAlertConfig_shouldNotAffectTypeCache() {
        Integer deviceId = 3003;
        Integer typeId = 3004;
        String deviceCacheKey = DEVICE_KEY_PREFIX + deviceId;
        String typeCacheKey = TYPE_KEY_PREFIX + typeId;

        cacheService.set(deviceCacheKey, buildAlertConfig("temperature", 80.0, 2), 1, TimeUnit.HOURS);
        cacheService.set(typeCacheKey, buildAlertConfig("pressure", 100.0, 1), 1, TimeUnit.HOURS);

        alertConfigService.evictDeviceAlertConfig(deviceId);

        assertFalse(cacheService.hasKey(deviceCacheKey), "设备缓存应被删除");
        assertTrue(cacheService.hasKey(typeCacheKey), "类型缓存应不受影响");
    }

    @Test
    @DisplayName("类型级缓存失效不影响设备级缓存 - 两级缓存独立管理")
    void evictTypeAlertConfig_shouldNotAffectDeviceCache() {
        Integer deviceId = 3005;
        Integer typeId = 3006;
        String deviceCacheKey = DEVICE_KEY_PREFIX + deviceId;
        String typeCacheKey = TYPE_KEY_PREFIX + typeId;

        cacheService.set(deviceCacheKey, buildAlertConfig("temperature", 80.0, 2), 1, TimeUnit.HOURS);
        cacheService.set(typeCacheKey, buildAlertConfig("pressure", 100.0, 1), 1, TimeUnit.HOURS);

        alertConfigService.evictTypeAlertConfig(typeId);

        assertTrue(cacheService.hasKey(deviceCacheKey), "设备缓存应不受影响");
        assertFalse(cacheService.hasKey(typeCacheKey), "类型缓存应被删除");
    }

    // ==================== 连续读取测试 ====================

    @Test
    @DisplayName("设备级连续读取 - 第一次miss查DB回填，第二次hit直接返回缓存")
    void getDeviceAlertConfig_twiceRead_shouldHitCacheOnSecondCall() {
        Integer deviceId = 4001;
        AlertRuleConfig dbConfig = buildAlertConfig("temperature", 80.0, 2);
        Device device = buildDevice(deviceId, 10, dbConfig);

        when(deviceService.getById(deviceId)).thenReturn(device);

        AlertRuleConfig first = alertConfigService.getDeviceAlertConfig(deviceId);
        assertNotNull(first, "第一次读取应返回DB数据");
        verify(deviceService, times(1)).getById(deviceId);

        reset(deviceService);

        AlertRuleConfig second = alertConfigService.getDeviceAlertConfig(deviceId);
        assertNotNull(second, "第二次读取应返回缓存数据");
        assertEquals("temperature", second.getRules().get(0).getMetricKey(), "第二次返回的metricKey应一致");
        verify(deviceService, never()).getById(anyInt());
    }

    @Test
    @DisplayName("类型级连续读取 - 第一次miss查DB回填，第二次hit直接返回缓存")
    void getTypeAlertConfig_twiceRead_shouldHitCacheOnSecondCall() {
        Integer typeId = 4002;
        AlertRuleConfig dbConfig = buildAlertConfig("voltage", 220.0, 2);
        DeviceType deviceType = buildDeviceType(typeId, dbConfig);

        when(deviceTypeService.getById(typeId)).thenReturn(deviceType);

        AlertRuleConfig first = alertConfigService.getTypeAlertConfig(typeId);
        assertNotNull(first, "第一次读取应返回DB数据");
        verify(deviceTypeService, times(1)).getById(typeId);

        reset(deviceTypeService);

        AlertRuleConfig second = alertConfigService.getTypeAlertConfig(typeId);
        assertNotNull(second, "第二次读取应返回缓存数据");
        assertEquals("voltage", second.getRules().get(0).getMetricKey(), "第二次返回的metricKey应一致");
        verify(deviceTypeService, never()).getById(anyInt());
    }
}
