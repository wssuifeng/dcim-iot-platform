package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DictMetric;

/*
 * 类功能:指标字典服务接口，定义指标字典的缓存刷新和缓存读取方法
 * */
public interface DictMetricService extends IService<DictMetric> {

    void refreshCache();

    DictMetric getFromCache(String metricKey);
}
