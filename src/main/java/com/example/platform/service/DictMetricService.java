package com.example.platform.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.entity.DictMetric;

/*
 * 类功能:指标字典服务接口，定义指标字典的缓存刷新和缓存读取方法
 * */
public interface DictMetricService extends IService<DictMetric> {

    /*
     * 方法功能:手动刷新指标字典缓存，清空后重新从数据库加载
     * 参数:无
     * 返回数据:无
     * */
    void refreshCache();

    /*
     * 方法功能:从缓存中获取指标字典，缓存未命中时查询数据库并回填
     * 参数:metricKey-指标标识
     * 返回数据:DictMetric-指标字典对象，未找到返回null
     * */
    DictMetric getFromCache(String metricKey);

    /*
     * 方法功能:根据ID删除指标字典条目，同时清除对应缓存
     * 参数:id-指标字典ID
     * 返回数据:boolean-删除是否成功
     * */
    boolean removeById(Integer id);
}
