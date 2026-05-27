package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.DictMetric;
import com.example.platform.mapper.DictMetricMapper;
import com.example.platform.service.CacheService;
import com.example.platform.service.DictMetricService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/*
 * 类功能:指标字典服务实现类，基于Redis缓存（CacheService）管理指标字典数据
 *
 * 【缓存策略】
 * - Key格式: dict:metric:{metricKey}，如 dict:metric:temperature
 * - 读流程: 缓存 → 数据库 → 回填（Cache-Aside模式）
 * - 写策略: 先更新DB，再删除缓存（避免并发不一致窗口）
 * - 防穿透: DB无数据时存入NullValue占位（TTL=5min），读取时判断instanceof返回null
 * - 手动刷新: deleteByPattern清除所有相关key，全量查询DB后multiSet批量回填
 *
 * 【为什么从ConcurrentHashMap迁移到Redis？】
 * - 多实例共享：3个服务节点需要看到同一份缓存数据
 * - 内存释放：JVM堆不再承载全量字典数据
 * - TTL管理：Redis原生支持过期，无需手动清理
 * */
@Service
public class DictMetricServiceImpl extends ServiceImpl<DictMetricMapper, DictMetric> implements DictMetricService {

    private static final String CACHE_KEY_PREFIX = "dict:metric:";
    private static final long CACHE_TTL_HOURS = 24;
    private static final long NULL_VALUE_TTL_MINUTES = 5;

    private final CacheService cacheService;

    public DictMetricServiceImpl(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /*
     * 方法功能:Bean初始化后全量加载指标字典到Redis缓存
     * 参数:无
     * 返回数据:无
     * */
    @PostConstruct
    public void initCache() {
        refreshAll();
    }

    /*
     * 方法功能:手动刷新指标字典缓存，清除所有相关key后全量回填
     * 参数:无
     * 返回数据:无
     *
     * 【刷新流程】
     * 1. deleteByPattern("dict:metric:*") 清除所有指标字典缓存
     * 2. 全量查询DB
     * 3. multiSet批量写入Redis（TTL=24h）
     *
     * 【SCAN的性能影响】
     * deleteByPattern底层使用SCAN游标迭代，数据量小时（几百条指标）几乎无影响
     * 数据量大时（万级以上），可考虑双key/版本号方案：
     *   - 写缓存时用带版本号的key：dict:metric:v2:temperature
     *   - 刷新时只需更新版本号指向v2，旧key自然过期
     * 当前指标字典数据量不大，SCAN方案完全够用
     * */
    @Override
    public void refreshCache() {
        refreshAll();
    }

    /*
     * 方法功能:从缓存中获取指标字典，缓存未命中时查询数据库并回填
     * 参数:metricKey-指标标识
     * 返回数据:DictMetric-指标字典对象，未找到返回null
     *
     * 【读流程（Cache-Aside模式）】
     * 1. 缓存命中且非NullValue → 直接返回
     * 2. 缓存命中但是NullValue → 返回null（防穿透：DB中确实没有这条数据）
     * 3. 缓存未命中 → 查询DB → 有数据存24h，无数据存NullValue占位5min
     * */
    @Override
    public DictMetric getFromCache(String metricKey) {
        String cacheKey = CACHE_KEY_PREFIX + metricKey;

        Object cached = cacheService.get(cacheKey, Object.class);
        if (cached != null) {
            if (cached instanceof CacheService.NullValue) {
                return null;
            }
            if (cached instanceof DictMetric) {
                return (DictMetric) cached;
            }
        }

        LambdaQueryWrapper<DictMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictMetric::getMetricKey, metricKey);
        DictMetric fromDb = getOne(wrapper);

        if (fromDb != null) {
            cacheService.set(cacheKey, fromDb, CACHE_TTL_HOURS, TimeUnit.HOURS);
            return fromDb;
        }

        cacheService.set(cacheKey, new CacheService.NullValue(), NULL_VALUE_TTL_MINUTES, TimeUnit.MINUTES);
        return null;
    }

    /*
     * 方法功能:保存指标字典条目，先写DB再删缓存
     * 参数:entity-待保存的指标字典对象
     * 返回数据:boolean-保存是否成功
     *
     * 【为什么先写DB再删缓存？】
     * 反过来"先删缓存再写DB"有并发不一致窗口：
     *   线程A删缓存 → 线程B读miss查DB(旧值)写缓存(旧值) → 线程A写DB(新值)
     *   → 缓存永远是旧值，直到TTL过期
     * 先写DB再删缓存的不一致窗口极窄（写DB比读DB慢得多），是业界推荐做法
     * */
    @Override
    public boolean save(DictMetric entity) {
        boolean result = super.save(entity);
        if (result && entity.getMetricKey() != null) {
            cacheService.delete(CACHE_KEY_PREFIX + entity.getMetricKey());
        }
        return result;
    }

    /*
     * 方法功能:更新指标字典条目，先写DB再删缓存
     * 参数:entity-待更新的指标字典对象
     * 返回数据:boolean-更新是否成功
     * */
    @Override
    public boolean updateById(DictMetric entity) {
        boolean result = super.updateById(entity);
        if (result && entity.getMetricKey() != null) {
            cacheService.delete(CACHE_KEY_PREFIX + entity.getMetricKey());
        }
        return result;
    }

    /*
     * 方法功能:根据ID删除指标字典条目，先写DB再删缓存
     * 参数:id-指标字典ID
     * 返回数据:boolean-删除是否成功
     * */
    @Override
    public boolean removeById(Integer id) {
        DictMetric existing = getById(id);
        boolean result = super.removeById(id);
        if (result && existing != null && existing.getMetricKey() != null) {
            cacheService.delete(CACHE_KEY_PREFIX + existing.getMetricKey());
        }
        return result;
    }

    /*
     * 方法功能:全量刷新指标字典缓存，清除旧缓存后从DB批量回填
     * 参数:无
     * 返回数据:无
     * */
    private void refreshAll() {
        cacheService.deleteByPattern(CACHE_KEY_PREFIX + "*");
        List<DictMetric> metrics = list();
        if (metrics.isEmpty()) {
            return;
        }
        HashMap<String, Object> map = new HashMap<>(metrics.size());
        for (DictMetric metric : metrics) {
            map.put(CACHE_KEY_PREFIX + metric.getMetricKey(), metric);
        }
        cacheService.multiSet(map, CACHE_TTL_HOURS, TimeUnit.HOURS);
    }
}
