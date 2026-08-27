package com.example.platform.ingest.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.platform.ingest.dto.IngestCredentialCreateDTO;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialUpdateDTO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.entity.IngestCredential;

/*
 * 类功能:网关接入凭证服务接口（机器身份管理：CRUD、启停、重置密钥、鉴权解析）
 *
 * 【安全语义】secret 仅创建/重置时一次性返回；查询接口永不返回。
 * 【鉴权语义】authenticate 按 accessKey 解析凭证并校验启用/过期状态，
 *   无效凭证抛 INGEST_UNAUTHORIZED（HTTP 场景由网关鉴权过滤器转 401 响应）。
 * */
public interface IngestCredentialService extends IService<IngestCredential> {

    /*
     * 方法功能:创建网关凭证（服务端生成 accessKey/secret，secret 仅本次返回）
     * 参数:createDTO-创建请求, currentUserId-操作用户
     * 返回数据:IngestCredentialCreatedVO-凭证视图与一次性 secret
     * */
    IngestCredentialCreatedVO createCredential(IngestCredentialCreateDTO createDTO, Long currentUserId);

    /*
     * 方法功能:更新网关显示名与过期时间（不允许改绑机房）
     * 参数:id-凭证ID, updateDTO-更新请求, currentUserId-操作用户
     * 返回数据:IngestCredentialVO-更新后的凭证视图
     * */
    IngestCredentialVO updateCredential(Long id, IngestCredentialUpdateDTO updateDTO, Long currentUserId);

    /*
     * 方法功能:启用/停用凭证（停用即切断该网关数据源）
     * 参数:id-凭证ID, status-目标状态 0-启用 1-停用, currentUserId-操作用户
     * 返回数据:IngestCredentialVO-更新后的凭证视图
     * */
    IngestCredentialVO updateStatus(Long id, Integer status, Long currentUserId);

    /*
     * 方法功能:重置签名密钥（旧 secret 立即失效，新 secret 仅本次返回）
     * 参数:id-凭证ID, currentUserId-操作用户
     * 返回数据:IngestCredentialCreatedVO-凭证视图与一次性新 secret
     * */
    IngestCredentialCreatedVO resetSecret(Long id, Long currentUserId);

    /*
     * 方法功能:删除凭证（物理删除；网关侧持有即失效）
     * 参数:id-凭证ID, currentUserId-操作用户
     * 返回数据:无
     * */
    void deleteCredential(Long id, Long currentUserId);

    /*
     * 方法功能:分页查询凭证（secret 不返回）
     * 参数:current/size-分页, keyword-网关名/accessKey 模糊, status-状态筛选, simulated-仿真标记筛选
     * 返回数据:Page<IngestCredentialVO>-凭证分页视图
     * */
    Page<IngestCredentialVO> pageCredentials(Integer current, Integer size,
                                             String keyword, Integer status, Integer simulated);

    /*
     * 方法功能:查询凭证详情（secret 不返回）
     * 参数:id-凭证ID
     * 返回数据:IngestCredentialVO-凭证视图
     * */
    IngestCredentialVO getCredential(Long id);

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
