package com.example.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.DictMetric;
import com.example.platform.mapper.DictMetricMapper;
import com.example.platform.service.DictMetricService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 类功能:指标字典服务实现类，维护指标字典的内存缓存，支持启动时加载和手动刷新
 * */
@Service
public class DictMetricServiceImpl extends ServiceImpl<DictMetricMapper, DictMetric> implements DictMetricService {

    private final ConcurrentHashMap<String, DictMetric> metricCache = new ConcurrentHashMap<>();

    /*
     * 方法功能:Bean初始化后加载指标字典缓存
     * 参数:无
     * 返回数据:无
     * */
    @PostConstruct
    public void initCache() {
        loadCache();
    }

    /*
     * 方法功能:手动刷新指标字典缓存，清空后重新从数据库加载
     * 参数:无
     * 返回数据:无
     * */
    @Override
    public void refreshCache() {
        metricCache.clear();
        loadCache();
    }

    /*
     * 方法功能:从缓存中获取指标字典，缓存未命中时查询数据库并回填
     * 参数:metricKey-指标标识
     * 返回数据:DictMetric-指标字典对象，未找到返回null
     * */
    @Override
    public DictMetric getFromCache(String metricKey) {
        DictMetric cached = metricCache.get(metricKey);
        if (cached != null) {
            return cached;
        }
        LambdaQueryWrapper<DictMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictMetric::getMetricKey, metricKey);
        DictMetric fromDb = getOne(wrapper);
        if (fromDb != null) {
            metricCache.put(metricKey, fromDb);
        }
        return fromDb;
    }

    /*
     * 方法功能:保存指标字典条目，同时更新缓存
     * 参数:entity-待保存的指标字典对象
     * 返回数据:boolean-保存是否成功
     * */
    @Override
    public boolean save(DictMetric entity) {
        boolean result = super.save(entity);
        if (result && entity.getMetricKey() != null) {
            metricCache.put(entity.getMetricKey(), entity);
        }
        return result;
    }

    /*
     * 方法功能:更新指标字典条目，更新后刷新缓存
     * 参数:entity-待更新的指标字典对象
     * 返回数据:boolean-更新是否成功
     * */
    @Override
    public boolean updateById(DictMetric entity) {
        boolean result = super.updateById(entity);
        if (result) {
            refreshCache();
        }
        return result;
    }

    /*
     * 方法功能:根据ID删除指标字典条目，同时清除对应缓存
     * 参数:id-指标字典ID
     * 返回数据:boolean-删除是否成功
     * */
    @Override
    public boolean removeById(Integer id) {
        DictMetric existing = getById(id);
        boolean result = super.removeById(id);
        if (result && existing != null && existing.getMetricKey() != null) {
            metricCache.remove(existing.getMetricKey());
        }
        return result;
    }

    /*
     * 方法功能:从数据库加载全部指标字典到内存缓存
     * 参数:无
     * 返回数据:无
     * */
    private void loadCache() {
        List<DictMetric> metrics = list();
        for (DictMetric metric : metrics) {
            metricCache.put(metric.getMetricKey(), metric);
        }
    }
}
