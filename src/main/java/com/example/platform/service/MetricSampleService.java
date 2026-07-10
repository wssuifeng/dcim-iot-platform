package com.example.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.dto.MetricSampleBatchDTO;
import com.example.platform.dto.MetricSampleDTO;
import com.example.platform.dto.MetricSampleVO;
import com.example.platform.entity.MetricSample;

import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:指标采样历史服务接口
 *
 * 【角色与范围】ORG_ADMIN/OPERATOR 可写（按设备范围）；所有用户按数据范围只读查询
 *
 * 写入完成后，应同步刷新 metric_latest（通过 MetricLatestService.upsertLatest）。
 * 查询时间范围不应超过保留策略上限（90 天），防止恶意整库扫描。
 * */
public interface MetricSampleService extends IService<MetricSample> {

    MetricSample saveSample(MetricSampleDTO dto, Long currentUserId);

    List<MetricSampleVO> saveBatch(MetricSampleBatchDTO batchDTO, Long currentUserId);

    Page<MetricSampleVO> pageSamples(Long currentUserId, Integer current, Integer size,
                                     Integer deviceId, Long pointId,
                                     LocalDateTime startTime, LocalDateTime endTime);
}