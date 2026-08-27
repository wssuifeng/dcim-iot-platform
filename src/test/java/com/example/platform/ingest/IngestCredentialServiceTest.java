package com.example.platform.ingest;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.ingest.dto.IngestCredentialCreateDTO;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 网关凭证服务集成测试（真实数据库，事务内回滚）
 */
@SpringBootTest
@Transactional
@Sql(scripts = "classpath:test-data-ingest.sql", config = @SqlConfig(encoding = "UTF-8"))
class IngestCredentialServiceTest {

    @Autowired
    private IngestCredentialService credentialService;

    @Test
    void createGeneratesAccessKeyAndSecret() {
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO("测试网关1", -2001L), 1L);

        assertThat(created.getAccessKey()).startsWith("gw-").hasSize(19);
        assertThat(created.getSecret()).startsWith("sk-").hasSize(35);
        assertThat(created.getGatewayName()).isEqualTo("测试网关1");
        assertThat(created.getCloudRoomId()).isEqualTo(-2001L);
        assertThat(created.getStatus()).isZero();
    }

    @Test
    void createWithDuplicateRoomRejected() {
        IngestCredentialCreateDTO createDTO = createDTO("幽灵网关", -9999L);

        assertThatCode(() -> credentialService.createCredential(createDTO, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机房不存在");
    }

    @Test
    void createWithCustomAccessKeyConflictRejected() {
        // 用自定义标识先建一个
        IngestCredentialCreateDTO dto = new IngestCredentialCreateDTO();
        dto.setGatewayName("网关甲");
        dto.setCloudRoomId(-2001L);
        dto.setAccessKey("gw-custom-key");
        credentialService.createCredential(dto, 1L);

        // 再用同一标识建 → 冲突
        IngestCredentialCreateDTO dto2 = new IngestCredentialCreateDTO();
        dto2.setGatewayName("网关乙");
        dto2.setCloudRoomId(-2001L);
        dto2.setAccessKey("gw-custom-key");
        assertThatCode(() -> credentialService.createCredential(dto2, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("接入标识已存在");
    }

    @Test
    void resetSecretInvalidatesOldSecret() {
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO("网关丙", -2001L), 1L);
        String oldSecret = created.getSecret();

        IngestCredentialCreatedVO reset = credentialService.resetSecret(created.getId(), 1L);

        assertThat(reset.getSecret()).startsWith("sk-").isNotEqualTo(oldSecret);
        IngestCredential reloaded = credentialService.getById(created.getId());
        assertThat(reloaded.getSecret()).isEqualTo(reset.getSecret());
    }

    @Test
    void authenticateRejectsDisabledCredential() {
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO("网关丁", -2001L), 1L);
        credentialService.updateStatus(created.getId(), 1, 1L);

        assertThatCode(() -> credentialService.authenticate(created.getAccessKey()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void authenticateRejectsUnknownKey() {
        assertThatCode(() -> credentialService.authenticate("gw-not-exists"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("网关凭证无效");
    }

    @Test
    void pageCredentialsFiltersByKeyword() {
        credentialService.createCredential(createDTO("检索目标网关", -2001L), 1L);
        credentialService.createCredential(createDTO("另一台网关", -2002L), 1L);

        Page<IngestCredentialVO> page = credentialService.pageCredentials(1, 10, "检索目标", null, null);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getGatewayName()).isEqualTo("检索目标网关");
    }

    @Test
    void updateCredentialCannotChangeRoom() {
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO("网关戊", -2001L), 1L);

        com.example.platform.ingest.dto.IngestCredentialUpdateDTO updateDTO =
                new com.example.platform.ingest.dto.IngestCredentialUpdateDTO();
        updateDTO.setGatewayName("网关戊改名");
        IngestCredentialVO updated = credentialService.updateCredential(created.getId(), updateDTO, 1L);

        assertThat(updated.getGatewayName()).isEqualTo("网关戊改名");
        // 机房未变：更新 DTO 根本没有机房字段
        assertThat(updated.getCloudRoomId()).isEqualTo(-2001L);
    }

    private IngestCredentialCreateDTO createDTO(String name, Long roomId) {
        IngestCredentialCreateDTO createDTO = new IngestCredentialCreateDTO();
        createDTO.setGatewayName(name);
        createDTO.setCloudRoomId(roomId);
        return createDTO;
    }
}
