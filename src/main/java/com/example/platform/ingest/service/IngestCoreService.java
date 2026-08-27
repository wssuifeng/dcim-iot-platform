package com.example.platform.ingest.service;

import com.example.platform.ingest.dto.IngestResultVO;
import com.example.platform.ingest.dto.IngestSampleBatchDTO;
import com.example.platform.ingest.entity.IngestCredential;

/*
 * 类功能:数据接收门传输无关核心服务，HTTP 与 MQTT 适配器汇入的同一条上报处理链
 *
 * 【处理链】deviceCode/pointCode 解析 → 机房归属校验 → 幂等去重 → 复用采样管线落库
 *   （saveSample → metric_latest → 设备在线刷新 → AFTER_COMMIT 告警评估与通知）。
 * 【批量语义】逐条独立事务，部分成功合法；重复 deviceCode+pointCode+seq 计入
 *   duplicated 不报错（MQTT QoS1 至少一次投递的重发是常态）。
 * 【审计语义】currentUserId 传 credentialId，采样管线内仅做存在性检查。
 * */
public interface IngestCoreService {

    /*
     * 方法功能:处理一批网关上报采样（逐条独立事务，部分成功合法）
     * 参数:credential-已验签的网关凭证, batch-上报批量数据
     * 返回数据:IngestResultVO-accepted/duplicated/failed 与失败明细
     * */
    IngestResultVO processSamples(IngestCredential credential, IngestSampleBatchDTO batch);

    /*
     * 方法功能:网关心跳保活，刷新凭证 last_seen_at（离线观测依据）
     * 参数:credential-已验签的网关凭证
     * 返回数据:无
     * */
    void heartbeat(IngestCredential credential);
}
