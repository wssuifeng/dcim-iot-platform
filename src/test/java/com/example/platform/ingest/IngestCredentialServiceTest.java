package com.example.platform.ingest;

import com.example.platform.common.ResultCode;
import com.example.platform.common.exception.BusinessException;
import com.example.platform.dto.CloudRoomCreateDTO;
import com.example.platform.dto.CloudRoomCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.dto.IngestCredentialVO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.service.CloudRoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 机房配套凭证（1:1 合一模型）集成测试：机房创建即建凭证、密钥重置、鉴权、级联删除
 */
@SpringBootTest
@Transactional
@Sql(scripts = "classpath:test-data-ingest.sql", config = @SqlConfig(encoding = "UTF-8"))
class IngestCredentialServiceTest {

    @Autowired
    private CloudRoomService cloudRoomService;
    @Autowired
    private IngestCredentialService credentialService;

    @Test
    void createRoomInitializesCredentialWithKeys() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一测试机房", "IT-UNIFY-ROOM"), 1L);

        assertThat(created.getAccessKey()).startsWith("gw-").hasSize(19);
        assertThat(created.getSecret()).startsWith("sk-").hasSize(35);
        // 凭证已挂机房且自动注册默认开启
        IngestCredential credential = credentialService.findCredentialByRoom(created.getId());
        assertThat(credential).isNotNull();
        assertThat(credential.getAutoRegister()).isEqualTo(1);
    }

    @Test
    void duplicateRoomCredentialRejected() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一机房甲", "IT-UNIFY-A"), 1L);

        // 1:1 兜底：同一机房再建凭证被拒
        assertThatCode(() -> credentialService.createCredentialForRoom(created.getId(), 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在接入凭证");
    }

    @Test
    void resetSecretInvalidatesOldSecret() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一机房乙", "IT-UNIFY-B"), 1L);

        IngestCredentialCreatedVO reset = credentialService.resetSecretByRoom(created.getId(), 1L);

        assertThat(reset.getSecret()).startsWith("sk-").isNotEqualTo(created.getSecret());
        IngestCredential reloaded = credentialService.findCredentialByRoom(created.getId());
        assertThat(reloaded.getSecret()).isEqualTo(reset.getSecret());
    }

    @Test
    void authenticateRejectsDisabledCredential() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一机房丙", "IT-UNIFY-C"), 1L);
        credentialService.updateStatusByRoom(created.getId(), 1, 1L);

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
    void updateAutoRegisterViaRoom() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一机房丁", "IT-UNIFY-D"), 1L);

        IngestCredentialVO updated = credentialService.updateAutoRegister(
                created.getId(), 0, 4, 1L);

        assertThat(updated.getAutoRegister()).isZero();
        assertThat(updated.getDefaultTypeId()).isEqualTo(4);
    }

    @Test
    void deleteRoomCascadesCredential() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一机房戊", "IT-UNIFY-E"), 1L);
        assertThat(credentialService.findCredentialByRoom(created.getId())).isNotNull();

        cloudRoomService.deleteCloudRoom(created.getId(), 1L);

        assertThat(credentialService.findCredentialByRoom(created.getId())).isNull();
    }

    @Test
    void getCredentialByRoomVOHidesSecret() {
        CloudRoomCreatedVO created = cloudRoomService.createCloudRoom(
                room("合一机房己", "IT-UNIFY-F"), 1L);

        IngestCredentialVO vo = credentialService.getCredentialByRoomVO(created.getId());

        assertThat(vo.getAccessKey()).isEqualTo(created.getAccessKey());
        org.assertj.core.api.Assertions.assertThat(vo).hasNoNullFieldsOrPropertiesExcept(
                "secret", "expireAt", "defaultTypeId", "lastSeenAt", "createTime", "telemetryToken", "telemetryDefaultDevice");
        // VO 无 secret 字段——类型系统保证不泄露
    }

    private CloudRoomCreateDTO room(String name, String code) {
        CloudRoomCreateDTO dto = new CloudRoomCreateDTO();
        dto.setRoomName(name);
        dto.setRoomCode(code);
        return dto;
    }
}
