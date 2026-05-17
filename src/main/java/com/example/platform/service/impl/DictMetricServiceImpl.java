package com.example.platform.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.entity.DictMetric;
import com.example.platform.mapper.DictMetricMapper;
import com.example.platform.service.DictMetricService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DictMetricServiceImpl extends ServiceImpl<DictMetricMapper, DictMetric> implements DictMetricService {

    // 本地内存缓存容器
    private final Map<String, DictMetric> metricCacheMap = new ConcurrentHashMap<>();

    /**
     * 项目启动时，自动加载全量配置到缓存
     */
    @PostConstruct
    @Override
    public void initCache() {
        refreshCache();
        log.info("✅ 指标字典缓存初始化完成，共 {} 项", metricCacheMap.size());
    }

    /**
     * 刷新缓存的方法
     */
    @Override
    public void refreshCache() {
        metricCacheMap.clear();
        List<DictMetric> list = this.list();
        for (DictMetric metric : list) {
            metricCacheMap.put(metric.getMetricKey(), metric);
        }
    }

    /**
     * 高频读取方法：直接走内存缓存
     */
    @Override
    public DictMetric getFromCache(String metricKey) {
        if (!StringUtils.hasText(metricKey)) {
            return null;
        }
        return metricCacheMap.get(metricKey);
    }

    // --- 重写增删改方法，自动维护缓存一致性 ---

    @Override
    public boolean save(DictMetric entity) {
        boolean flag = super.save(entity);
        if (flag) {
            // 新增成功后，将其放入缓存
            metricCacheMap.put(entity.getMetricKey(), entity);
        }
        return flag;
    }

    @Override
    public boolean updateById(DictMetric entity) {
        boolean flag = super.updateById(entity);
        if (flag) {
            // 更新成功后，覆盖缓存中的旧数据
            metricCacheMap.put(entity.getMetricKey(), entity);
        }
        return flag;
    }

    @Override
    public boolean removeById(Integer id) {
        DictMetric entity = super.getById(id);
        boolean flag = super.removeById(id);
        if (flag && entity != null) {
            // 删除成功后，将缓存中的数据移除
            metricCacheMap.remove(entity.getMetricKey());
        }
        return flag;
    }
}
