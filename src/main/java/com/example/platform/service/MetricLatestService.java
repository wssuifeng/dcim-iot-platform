package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.MetricLatestVO;
import com.example.platform.entity.MetricLatest;

import java.util.List;

/*
 * 类功能:指标最新值服务接口
 *
 * 【角色与范围】所有已认证用户按数据范围查询最新值；由 Sample 写入流程内部 upsert
 * */
public interface MetricLatestService extends IService<MetricLatest> {

    /*
     * 方法功能:写入采样时刷新最新值（upsert）
     * 逻辑：按 (deviceId, pointId) 查找：
     *   - 存在且新采样时间 >= current latest_time → 更新 latest_value / latest_time
     *   - 不存在 → 插入
     *   - 早于已存在的时间 → 忽略，保持当前最新值快照
     * */
    MetricLatest upsertLatest(Integer deviceId, Long pointId,
                              java.math.BigDecimal value, java.time.LocalDateTime sampleTime);

    /*
     * 方法功能:按设备查询所有点位的最新值（带点位名称/编码/单位）
     * */
    List<MetricLatestVO> listByDevice(Integer deviceId, Long currentUserId);

    /*
     * 方法功能:分页查询指标最新值
     * */
    Page<MetricLatestVO> pageLatest(Long currentUserId, Integer current, Integer size,
                                    Integer deviceId, Long pointId);
}