package com.example.platform.service;

import com.example.platform.dto.DashboardStatsDTO;

/*
 * 类功能:仪表盘统计服务接口，提供仪表盘统计数据的查询功能
 *
 * 【缓存策略——互斥锁防击穿】
 * 仪表盘统计数据是典型的读多写少场景，缓存失效瞬间大量并发请求同时打到数据库，
 * 这就是"缓存击穿"问题。本服务采用互斥锁方案：
 *   1. 缓存命中 → 直接返回，不查DB
 *   2. 缓存未命中 → 尝试获取互斥锁（SETNX）
 *     - 获锁成功 → 查DB + 回填缓存 + 释放锁
 *     - 获锁失败 → 短暂等待后重试读缓存
 *
 * 【为什么用互斥锁而非随机TTL？】
 * 仪表盘是单个Key（stats:dashboard），随机TTL偏移无法分散并发压力，
 * 只有一个Key失效时，所有请求仍然同时打到DB。互斥锁能限制只有一个线程重建缓存。
 * */
public interface DashboardStatsService {

    /*
     * 方法功能:获取仪表盘统计数据（带缓存和互斥锁防击穿）
     * 参数:无
     * 返回数据:DashboardStatsDTO-包含设备统计和告警统计
     * */
    DashboardStatsDTO getDashboardStats();
}
