package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleBatchResultVO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.MetricSample;

import java.time.LocalDateTime;

/*
 * 类功能:指标采样历史服务接口（T1 采样事务唯一所有者）
 *
 * 【角色与范围】ORG_ADMIN/OPERATOR 可写（按设备范围）；所有用户按数据范围只读查询
 *
 * 【T1 采样事务】INSERT metric_sample + UPSERT metric_latest + 刷设备在线状态(status=1)；
 *   事务提交后发布 MetricSampleSavedEvent（仅向前传播至告警评估）。
 *   T1 内禁止调用任何告警类（依赖单向铁律）。
 *
 * 【写入分流】triggerAlarm=true（HTTP上报）发布事件触发告警评估；
 *   triggerAlarm=false（模拟运行，由 simulation.alarm-trigger-enabled 开关控制）只落库不评估。
 *
 * 【批量语义（D10）】逐条独立处理（TransactionTemplate 编程式事务），部分成功合法；
 *   返回 successCount 与逐条失败明细，任一失败不影响其余。
 *
 * 【查询约束】时间范围不应超过保留策略上限（90 天），防止恶意整库扫描。
 * */
public interface MetricSampleService extends IService<MetricSample> {

    /*
     * 方法功能:单条写入采样（T1 事务：采样+最新值+设备在线状态，提交后条件发布评估事件）
     * 参数:dto-采样数据, currentUserId-当前用户, triggerAlarm-是否触发告警评估
     * 返回数据:MetricSample-写入后的采样实体
     * */
    MetricSample saveSample(MetricSampleDTO dto, Long currentUserId, boolean triggerAlarm);

    /*
     * 方法功能:批量逐条独立写入（非原子，部分成功合法）
     * 参数:batchDTO-批量数据, currentUserId-当前用户, triggerAlarm-是否触发告警评估
     * 返回数据:MetricSampleBatchResultVO-成功条数与失败明细
     * */
    MetricSampleBatchResultVO saveBatch(MetricSampleBatchDTO batchDTO, Long currentUserId,
                                        boolean triggerAlarm);

    /*
     * 方法功能:分页查询采样历史（须指定 deviceId，经设备范围校验，跨度≤90天）
     * 参数:currentUserId-当前用户, current/size-分页, deviceId-设备ID, pointId-点位ID, startTime/endTime-时间范围
     * 返回数据:Page<MetricSampleVO>-采样分页（含点位名称回填）
     * */
    Page<MetricSampleVO> pageSamples(Long currentUserId, Integer current, Integer size,
                                     Integer deviceId, Long pointId,
                                     LocalDateTime startTime, LocalDateTime endTime);
}
