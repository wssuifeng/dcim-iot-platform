package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DictMetric;

/*
 * 类功能:指标字典服务接口，定义指标字典的缓存刷新方法
 * */
public interface DictMetricService extends IService<DictMetric> {

    /*
     * 方法功能:手动刷新指标字典的内存缓存
     * 参数:无
     * 返回数据:无
     * */
    void refreshCache();
}
