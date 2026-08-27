package com.example.platform.ingest.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.common.util.PaginationUtils;
import com.example.platform.ingest.dto.IngestCredentialCreateDTO;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialUpdateDTO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.mapper.IngestCredentialMapper;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.service.CloudRoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/*
 * 类功能:网关接入凭证服务实现（accessKey/secret 生成、CRUD、鉴权解析、存活刷新）
 *
 * 【密钥生成】SecureRandom 十六进制随机串；accessKey 唯一冲突时生成侧重试。
 * 【鉴权校验】状态非0启用或已过期一律抛 INGEST_UNAUTHORIZED（不区分细节，防探测）。
 * */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestCredentialServiceImpl extends ServiceImpl<IngestCredentialMapper, IngestCredential>
        implements IngestCredentialService {

    private final CloudRoomService cloudRoomService;
    private final com.example.platform.service.AlarmEventService alarmEventService;
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
    public IngestCredentialCreatedVO createCredential(IngestCredentialCreateDTO createDTO, Long currentUserId) {
        if (cloudRoomService.getById(createDTO.getCloudRoomId()) == null) {
            throw new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "绑定机房不存在");
        }

        IngestCredential credential = new IngestCredential();
        if (StringUtils.hasText(createDTO.getAccessKey())) {
            ensureAccessKeyAbsent(createDTO.getAccessKey());
            credential.setAccessKey(createDTO.getAccessKey());
        } else {
            credential.setAccessKey(generateUniqueAccessKey());
        }
        credential.setSecret(generateSecret());
        credential.setGatewayName(createDTO.getGatewayName());
        credential.setCloudRoomId(createDTO.getCloudRoomId());
        credential.setSimulated(createDTO.getSimulated() == null ? 0 : createDTO.getSimulated());
        int autoRegister = createDTO.getAutoRegister() == null ? 0 : createDTO.getAutoRegister();
        credential.setAutoRegister(autoRegister);
        credential.setDefaultTypeId(createDTO.getDefaultTypeId());
        credential.setStatus(0);
        credential.setExpireAt(createDTO.getExpireAt());
        save(credential);
        log.info("网关凭证创建 credentialId={} accessKey={} roomId={} operator={}",
                credential.getId(), credential.getAccessKey(), credential.getCloudRoomId(), currentUserId);
        return toCreatedVO(credential);
    }

    @Override
    public IngestCredentialVO updateCredential(Long id, IngestCredentialUpdateDTO updateDTO, Long currentUserId) {
        IngestCredential credential = getCredentialOrThrow(id);
        credential.setGatewayName(updateDTO.getGatewayName());
        credential.setExpireAt(updateDTO.getExpireAt());
        updateById(credential);
        return IngestCredentialVO.fromEntity(credential);
    }

    @Override
    public IngestCredentialVO updateStatus(Long id, Integer status, Long currentUserId) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "状态值只能为0或1");
        }
        IngestCredential credential = getCredentialOrThrow(id);
        credential.setStatus(status);
        updateById(credential);
        log.info("网关凭证状态变更 credentialId={} status={} operator={}", id, status, currentUserId);
        return IngestCredentialVO.fromEntity(credential);
    }

    @Override
    public IngestCredentialCreatedVO resetSecret(Long id, Long currentUserId) {
        IngestCredential credential = getCredentialOrThrow(id);
        credential.setSecret(generateSecret());
        updateById(credential);
        log.info("网关凭证密钥重置 credentialId={} operator={}", id, currentUserId);
        return toCreatedVO(credential);
    }

    @Override
    public void deleteCredential(Long id, Long currentUserId) {
        getCredentialOrThrow(id);
        removeById(id);
        log.info("网关凭证删除 credentialId={} operator={}", id, currentUserId);
    }

    @Override
    public Page<IngestCredentialVO> pageCredentials(Integer current, Integer size,
                                                    String keyword, Integer status, Integer simulated) {
        int[] params = PaginationUtils.validate(current, size);
        LambdaQueryWrapper<IngestCredential> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(IngestCredential::getGatewayName, kw)
                    .or().like(IngestCredential::getAccessKey, kw));
        }
        wrapper.eq(status != null, IngestCredential::getStatus, status);
        wrapper.eq(simulated != null, IngestCredential::getSimulated, simulated);
        wrapper.orderByDesc(IngestCredential::getId);

        Page<IngestCredential> page = page(new Page<>(params[0], params[1]), wrapper);
        Page<IngestCredentialVO> result = new Page<>(params[0], params[1], page.getTotal());
        List<IngestCredentialVO> records = page.getRecords().stream()
                .map(IngestCredentialVO::fromEntity).toList();
        result.setRecords(records);
        return result;
    }

    @Override
    public IngestCredentialVO getCredential(Long id) {
        return IngestCredentialVO.fromEntity(getCredentialOrThrow(id));
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
                && credential.getLastSeenAt().isBefore(LocalDateTime.now().minusSeconds(gatewayOfflineThresholdSeconds));
        IngestCredential update = new IngestCredential();
        update.setId(credentialId);
        update.setLastSeenAt(LocalDateTime.now());
        updateById(update);
        if (wasStale) {
            try {
                alarmEventService.autoRecover(null, null, "GATEWAY_OFFLINE");
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

    private void ensureAccessKeyAbsent(String accessKey) {
        Long count = lambdaQuery().eq(IngestCredential::getAccessKey, accessKey).count();
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.INGEST_ACCESS_KEY_EXISTS, "接入标识已存在");
        }
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
        vo.setGatewayName(credential.getGatewayName());
        vo.setCloudRoomId(credential.getCloudRoomId());
        vo.setSimulated(credential.getSimulated());
        vo.setAutoRegister(credential.getAutoRegister());
        vo.setDefaultTypeId(credential.getDefaultTypeId());
        vo.setStatus(credential.getStatus());
        vo.setLastSeenAt(credential.getLastSeenAt());
        vo.setExpireAt(credential.getExpireAt());
        vo.setCreateTime(credential.getCreateTime());
        return vo;
    }
}
