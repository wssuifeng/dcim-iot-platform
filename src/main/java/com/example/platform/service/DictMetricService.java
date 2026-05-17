package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DictMetric;

public interface DictMetricService extends IService<DictMetric> {

    /**
     * 初始化缓存（项目启动时自动执行）
     */
    void initCache();

    /**
     * 刷新缓存（管理页面手动触发）
     */
    void refreshCache();

    /**
     * 从缓存中获取指标元数据（高频读取）
     */
    DictMetric getFromCache(String metricKey);
    /*
    * 根据ID删除
    * */
    boolean removeById(Integer id);
}
