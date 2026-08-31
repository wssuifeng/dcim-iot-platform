package com.example.platform.ingest.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.entity.IngestCredential;

/*
 * 类功能:网关接入凭证服务接口——机房配套凭证（1:1）的生命周期与机器鉴权
 *
 * 【合一模型】凭证随机房生灭：创建机房即建凭证（auto_register 默认开启）、
 *   删除机房级联删、停用机房联动停。本接口不再独立暴露 CRUD 管理面。
 * 【安全语义】secret 仅创建/重置时一次性返回；查询接口永不返回。
 * 【鉴权语义】authenticate 按 accessKey 解析凭证并校验启用/过期状态，
 *   无效凭证抛 INGEST_UNAUTHORIZED。
 * */
public interface IngestCredentialService extends IService<IngestCredential> {

    /*
     * 方法功能:为机房创建配套凭证（机房创建时由 CloudRoomService 调用，同一事务）
     * 参数:cloudRoomId-机房ID, currentUserId-操作用户
     * 返回数据:IngestCredentialCreatedVO-凭证视图与一次性 secret
     * */
    IngestCredentialCreatedVO createCredentialForRoom(Long cloudRoomId, Long currentUserId);

    /*
     * 方法功能:更新机房凭证的自动注册配置
     * 参数:roomId-机房ID, autoRegister-自动注册开关（可空不改）, defaultTypeId-默认类型（可空不改）, currentUserId-操作用户
     * 返回数据:IngestCredentialVO-凭证视图
     * */
    IngestCredentialVO updateAutoRegister(Long roomId, Integer autoRegister, Integer defaultTypeId,
                                          Long currentUserId);

    /*
     * 方法功能:机房凭证状态联动变更（随机房启停）
     * 参数:roomId-机房ID, status-目标状态 0-启用 1-停用, currentUserId-操作用户
     * 返回数据:IngestCredentialVO-凭证视图
     * */
    IngestCredentialVO updateStatusByRoom(Long roomId, Integer status, Long currentUserId);

    /*
     * 方法功能:重置机房凭证密钥（旧 secret 立即失效，新 secret 仅本次返回）
     * 参数:roomId-机房ID, currentUserId-操作用户
     * 返回数据:IngestCredentialCreatedVO-凭证视图与一次性新 secret
     * */
    IngestCredentialCreatedVO resetSecretByRoom(Long roomId, Long currentUserId);

    /*
     * 方法功能:TB 兼容遥测 token 鉴权（ADR-008 D2：token→凭证→机房归属；启用状态与有效期同口径）
     * 参数:telemetryToken-URL 路径中的遥测 token
     * 返回数据:IngestCredential-启用中的凭证
     * */
    IngestCredential authenticateByTelemetryToken(String telemetryToken);

    /*
     * 方法功能:生成/重置 TB 兼容遥测 token 与默认设备绑定（重置旧 token 立即失效）
     * 参数:roomId-机房ID, defaultDevice-纯 TB 格式默认设备编码（可空=仅支持报文显式 device）, currentUserId-操作用户
     * 返回数据:IngestCredential-更新后的凭证（token 明文仅此一次随实体返回）
     * */
    IngestCredential issueTelemetryToken(Long roomId, String defaultDevice, Long currentUserId);

    /*
     * 方法功能:级联删除机房凭证（随机房删除）
     * 参数:roomId-机房ID, currentUserId-操作用户
     * 返回数据:无
     * */
    void deleteCredentialByRoom(Long roomId, Long currentUserId);

    /*
     * 方法功能:查询机房凭证视图（secret 不返回）
     * 参数:roomId-机房ID
     * 返回数据:IngestCredentialVO-凭证视图
     * */
    IngestCredentialVO getCredentialByRoomVO(Long roomId);

    /*
     * 方法功能:取机房凭证实体，不存在抛业务异常
     * 参数:roomId-机房ID
     * 返回数据:IngestCredential-凭证实体
     * */
    IngestCredential getCredentialByRoom(Long roomId);

    /*
     * 方法功能:取机房凭证实体，不存在返回 null
     * 参数:roomId-机房ID
     * 返回数据:IngestCredential-凭证实体或 null
     * */
    IngestCredential findCredentialByRoom(Long roomId);

    /*
     * 方法功能:按接入标识解析凭证并校验启用与过期状态（机器鉴权入口）
     * 参数:accessKey-接入标识
     * 返回数据:IngestCredential-有效凭证实体
     * */
    IngestCredential authenticate(String accessKey);

    /*
     * 方法功能:刷新凭证最近活跃时间（心跳/上报成功后调用，离线观测依据）
     * 参数:credentialId-凭证ID
     * 返回数据:无
     * */
    void refreshLastSeen(Long credentialId);
}
