package com.example.platform.ingest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.mapper.IngestCredentialMapper;
import com.example.platform.ingest.service.IngestCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;

/*
 * 类功能:网关接入凭证服务实现——机房配套凭证的生成、鉴权解析、存活刷新
 *
 * 【合一模型】凭证与机房 1:1（uk_ingest_room），生命周期由 CloudRoomService 驱动：
 *   创建机房即建凭证、删除机房级联删凭证、停用机房联动停凭证。
 * 【密钥生成】SecureRandom 十六进制随机串；accessKey 唯一冲突时生成侧重试。
 * 【鉴权校验】状态非0启用或已过期一律抛 INGEST_UNAUTHORIZED（不区分细节，防探测）。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestCredentialServiceImpl extends ServiceImpl<IngestCredentialMapper, IngestCredential>
        implements IngestCredentialService {

    // 懒获取：refreshLastSeen 的告警自动恢复需要 AlarmEventService，而其依赖链
    // （AlarmEvent→Device→CloudRoom→IngestCredential）与本服务构成构造器环，
    // 用 ObjectProvider 延迟解析打破启动期循环依赖
    private final org.springframework.beans.factory.ObjectProvider<com.example.platform.service.AlarmEventService> alarmEventServiceProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    // 网关离线判定阈值（与 IngestGatewayOfflineMonitorTask 同源配置）
    @org.springframework.beans.factory.annotation.Value("${task.gateway-offline-threshold-seconds:300}")
    private int gatewayOfflineThresholdSeconds;

    // accessKey: gw- + 16位十六进制（碰撞空间 2^64，重试 3 次足够）
    private static final String ACCESS_KEY_PREFIX = "gw-";
    private static final int ACCESS_KEY_RANDOM_HEX = 16;
    // secret: sk- + 32位十六进制（128 位熵）
    private static final String SECRET_PREFIX = "sk-";
    private static final int SECRET_RANDOM_HEX = 32;

    @Override
    public IngestCredentialCreatedVO createCredentialForRoom(Long cloudRoomId, Long currentUserId) {
        // 机房 1:1：已存在即拒绝（由机房服务保证，此处兜底防并发）
        Long existing = lambdaQuery().eq(IngestCredential::getCloudRoomId, cloudRoomId).count();
        if (existing != null && existing > 0) {
            throw new BusinessException(ResultCode.INGEST_ACCESS_KEY_EXISTS, "该机房已存在接入凭证");
        }
        IngestCredential credential = new IngestCredential();
        credential.setAccessKey(generateUniqueAccessKey());
        credential.setSecret(generateSecret());
        credential.setCloudRoomId(cloudRoomId);
        credential.setAutoRegister(1);
        credential.setStatus(0);
        save(credential);
        log.info("机房配套凭证创建 credentialId={} accessKey={} roomId={} operator={}",
                credential.getId(), credential.getAccessKey(), cloudRoomId, currentUserId);
        return toCreatedVO(credential);
    }

    @Override
    public IngestCredentialVO updateAutoRegister(Long roomId, Integer autoRegister, Integer defaultTypeId,
                                                 Long currentUserId) {
        IngestCredential credential = getCredentialByRoom(roomId);
        if (autoRegister != null) {
            credential.setAutoRegister(autoRegister);
        }
        if (defaultTypeId != null) {
            credential.setDefaultTypeId(defaultTypeId);
        }
        updateById(credential);
        log.info("机房凭证自动注册配置变更 roomId={} autoRegister={} defaultTypeId={} operator={}",
                roomId, credential.getAutoRegister(), credential.getDefaultTypeId(), currentUserId);
        return IngestCredentialVO.fromEntity(credential);
    }

    @Override
    public IngestCredentialVO updateStatusByRoom(Long roomId, Integer status, Long currentUserId) {
        IngestCredential credential = getCredentialByRoom(roomId);
        credential.setStatus(status);
        updateById(credential);
        log.info("机房凭证状态联动变更 roomId={} status={} operator={}", roomId, status, currentUserId);
        return IngestCredentialVO.fromEntity(credential);
    }

    @Override
    public IngestCredentialCreatedVO resetSecretByRoom(Long roomId, Long currentUserId) {
        IngestCredential credential = getCredentialByRoom(roomId);
        credential.setSecret(generateSecret());
        updateById(credential);
        log.info("机房凭证密钥重置 roomId={} operator={}", roomId, currentUserId);
        return toCreatedVO(credential);
    }

    @Override
    public void deleteCredentialByRoom(Long roomId, Long currentUserId) {
        IngestCredential credential = findCredentialByRoom(roomId);
        if (credential != null) {
            removeById(credential.getId());
            log.info("机房凭证级联删除 roomId={} credentialId={} operator={}",
                    roomId, credential.getId(), currentUserId);
        }
    }

    @Override
    public IngestCredentialVO getCredentialByRoomVO(Long roomId) {
        return IngestCredentialVO.fromEntity(getCredentialByRoom(roomId));
    }

    @Override
    public IngestCredential getCredentialByRoom(Long roomId) {
        IngestCredential credential = findCredentialByRoom(roomId);
        if (credential == null) {
            throw new BusinessException(ResultCode.INGEST_CREDENTIAL_NOT_FOUND,
                    "机房未初始化接入凭证: " + roomId);
        }
        return credential;
    }

    @Override
    public IngestCredential findCredentialByRoom(Long roomId) {
        return getOne(new LambdaQueryWrapper<IngestCredential>()
                .eq(IngestCredential::getCloudRoomId, roomId));
    }

    @Override
    public IngestCredential authenticate(String accessKey) {
        if (!StringUtils.hasText(accessKey)) {
            throw new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证无效");
        }
        IngestCredential credential = getOne(new LambdaQueryWrapper<IngestCredential>()
                .eq(IngestCredential::getAccessKey, accessKey));
        if (credential == null) {
            throw new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证无效");
        }
        if (credential.getStatus() != null && credential.getStatus() != 0) {
            throw new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证已停用");
        }
        if (credential.getExpireAt() != null && credential.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.INGEST_UNAUTHORIZED, "网关凭证已过期");
        }
        return credential;
    }

    @Override
    public void refreshLastSeen(Long credentialId) {
        IngestCredential credential = getById(credentialId);
        if (credential == null) {
            return;
        }
        // 心跳/上报成功即网关存活：存在活动 GATEWAY_OFFLINE 告警则自动恢复
        boolean wasStale = credential.getLastSeenAt() != null
                && credential.getLastSeenAt().isBefore(
                        LocalDateTime.now().minusSeconds(gatewayOfflineThresholdSeconds));
        IngestCredential update = new IngestCredential();
        update.setId(credentialId);
        update.setLastSeenAt(LocalDateTime.now());
        updateById(update);
        if (wasStale) {
            try {
                alarmEventServiceProvider.getObject().autoRecover(null, null, "GATEWAY_OFFLINE");
                log.info("网关恢复在线，GATEWAY_OFFLINE 告警自动恢复 credentialId={}", credentialId);
            } catch (Exception ex) {
                log.warn("网关恢复告警自动恢复失败 credentialId={} error={}", credentialId, ex.getMessage());
            }
        }
    }

    private IngestCredential getCredentialOrThrow(Long id) {
        IngestCredential credential = getById(id);
        if (credential == null) {
            throw new BusinessException(ResultCode.INGEST_CREDENTIAL_NOT_FOUND, "网关凭证不存在");
        }
        return credential;
    }

    private String generateUniqueAccessKey() {
        for (int i = 0; i < 3; i++) {
            String candidate = ACCESS_KEY_PREFIX + randomHex(ACCESS_KEY_RANDOM_HEX);
            Long count = lambdaQuery().eq(IngestCredential::getAccessKey, candidate).count();
            if (count == null || count == 0) {
                return candidate;
            }
        }
        throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "接入标识生成冲突，请重试");
    }

    private String generateSecret() {
        return SECRET_PREFIX + randomHex(SECRET_RANDOM_HEX);
    }

    private String randomHex(int charCount) {
        StringBuilder sb = new StringBuilder(charCount);
        for (int i = 0; i < charCount; i++) {
            sb.append(Character.forDigit(secureRandom.nextInt(16), 16));
        }
        return sb.toString();
    }

    private IngestCredentialCreatedVO toCreatedVO(IngestCredential credential) {
        IngestCredentialCreatedVO vo = new IngestCredentialCreatedVO();
        vo.setId(credential.getId());
        vo.setAccessKey(credential.getAccessKey());
        vo.setSecret(credential.getSecret());
        vo.setCloudRoomId(credential.getCloudRoomId());
        vo.setAutoRegister(credential.getAutoRegister());
        vo.setDefaultTypeId(credential.getDefaultTypeId());
        vo.setStatus(credential.getStatus());
        vo.setLastSeenAt(credential.getLastSeenAt());
        vo.setExpireAt(credential.getExpireAt());
        vo.setCreateTime(credential.getCreateTime());
        return vo;
    }
}
