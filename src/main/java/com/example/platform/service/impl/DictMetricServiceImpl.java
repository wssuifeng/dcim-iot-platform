package com.example.platform.service.impl;

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

    /** 指标字典内存缓存，key为metricKey，value为DictMetric对象 */
    private final ConcurrentHashMap<String, DictMetric> metricCache = new ConcurrentHashMap<>();

    /*
     * 方法功能:应用启动时自动加载指标字典到内存缓存
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
     * 方法功能:从数据库全量加载指标字典到内存缓存
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
