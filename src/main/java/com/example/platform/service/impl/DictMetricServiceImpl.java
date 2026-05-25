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

    @PostConstruct
    public void initCache() {
        loadCache();
    }

    @Override
    public void refreshCache() {
        metricCache.clear();
        loadCache();
    }

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

    @Override
    public boolean save(DictMetric entity) {
        boolean result = super.save(entity);
        if (result && entity.getMetricKey() != null) {
            metricCache.put(entity.getMetricKey(), entity);
        }
        return result;
    }

    @Override
    public boolean updateById(DictMetric entity) {
        boolean result = super.updateById(entity);
        if (result) {
            refreshCache();
        }
        return result;
    }

    @Override
    public boolean removeById(Integer id) {
        DictMetric existing = getById(id);
        boolean result = super.removeById(id);
        if (result && existing != null && existing.getMetricKey() != null) {
            metricCache.remove(existing.getMetricKey());
        }
        return result;
    }

    private void loadCache() {
        List<DictMetric> metrics = list();
        for (DictMetric metric : metrics) {
            metricCache.put(metric.getMetricKey(), metric);
        }
    }
}
