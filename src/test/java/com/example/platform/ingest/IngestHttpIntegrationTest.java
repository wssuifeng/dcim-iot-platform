package com.example.platform.ingest;

import com.example.platform.ingest.dto.IngestCredentialCreateDTO;
import com.example.platform.ingest.dto.IngestCredentialCreatedVO;
import com.example.platform.ingest.entity.IngestCredential;
import com.example.platform.ingest.service.IngestCredentialService;
import com.example.platform.ingest.util.GatewaySignUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 数据接收门 HTTP 通道集成测试：真实数据库 + 完整 Security 过滤链 + 网关机器鉴权。
 * 测试数据在事务内写入并回滚，不污染开发数据库。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Sql(scripts = "classpath:test-data-ingest.sql", config = @SqlConfig(encoding = "UTF-8"))
class IngestHttpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private IngestCredentialService credentialService;
    @Autowired
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Test
    void validSamplesAccepted() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        MvcResult result = postSamples(credential, 200,
                item("IT-DEV-A", 22.5, LocalDateTime.now(), 1L));

        JsonNode data = dataOf(result);
        assertThat(data.get("accepted").asInt()).isEqualTo(1);
        assertThat(data.get("duplicated").asInt()).isZero();
        assertThat(data.get("failed").asInt()).isZero();
    }

    @Test
    void duplicateSeqRejectedAsDuplicated() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        postSamples(credential, 200, item("IT-DEV-A", 22.5, LocalDateTime.now(), 42L));
        MvcResult second = postSamples(credential, 200,
                item("IT-DEV-A", 99.9, LocalDateTime.now(), 42L));

        JsonNode data = dataOf(second);
        assertThat(data.get("accepted").asInt()).isZero();
        assertThat(data.get("duplicated").asInt()).isEqualTo(1);
    }

    @Test
    void sameSeqDifferentPointIsNotDuplicate() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        // 幂等键 = deviceCode+pointCode+seq：同 seq 不同点位是两条合法数据
        MvcResult result = postSamples(credential, 200,
                item("IT-DEV-A", 22.5, LocalDateTime.now(), 7L),
                itemVoltage("IT-DEV-A", 220.1, LocalDateTime.now(), 7L));

        assertThat(dataOf(result).get("accepted").asInt()).isEqualTo(2);
    }

    @Test
    void crossRoomDeviceRejected() throws Exception {
        // 凭证绑机房A，上报机房B设备 → 越权条目 failed 不落库
        IngestCredential credential = createCredential(-2001L);

        MvcResult result = postSamples(credential, 200,
                item("IT-DEV-B", 22.5, LocalDateTime.now(), 1L));

        JsonNode data = dataOf(result);
        assertThat(data.get("accepted").asInt()).isZero();
        assertThat(data.get("failed").asInt()).isEqualTo(1);
        assertThat(data.get("failures").get(0).get("errorCode").asInt()).isEqualTo(4098);
    }

    @Test
    void unknownDeviceAndUnboundPointFailWithoutBlockingOthers() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        MvcResult result = postSamples(credential, 200,
                item("IT-UNKNOWN", 22.5, LocalDateTime.now(), 1L),
                item("IT-DEV-A", 22.5, LocalDateTime.now(), 2L),
                // 设备C 同机房但绑定停用 → 绑定校验失败
                item("IT-DEV-C", 30.0, LocalDateTime.now(), 3L));

        JsonNode data = dataOf(result);
        assertThat(data.get("accepted").asInt()).isEqualTo(1);
        assertThat(data.get("failed").asInt()).isEqualTo(2);
        assertThat(data.get("failures").toString()).contains("4023").contains("未启用点位");
    }

    @Test
    void backfillOldSampleAccepted() throws Exception {
        // 断网补传：早于平台时间的采样合法
        IngestCredential credential = createCredential(-2001L);

        MvcResult result = postSamples(credential, 200,
                item("IT-DEV-A", 20.1, LocalDateTime.now().minusHours(2), 1L));

        assertThat(dataOf(result).get("accepted").asInt()).isEqualTo(1);
    }

    @Test
    void futureSampleBeyondToleranceRejected() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        MvcResult result = postSamples(credential, 200,
                item("IT-DEV-A", 25.0, LocalDateTime.now().plusMinutes(10), 1L));

        JsonNode data = dataOf(result);
        assertThat(data.get("failed").asInt()).isEqualTo(1);
        assertThat(data.get("failures").get(0).get("errorCode").asInt()).isEqualTo(4031);
    }

    @Test
    void missingAuthorizationHeaderRejected() throws Exception {
        IngestCredential credential = createCredential(-2001L);
        String body = bodyOf(item("IT-DEV-A", 22.5, LocalDateTime.now(), 1L));

        mockMvc.perform(post("/ingest/samples").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongSignatureRejected() throws Exception {
        IngestCredential credential = createCredential(-2001L);
        String body = bodyOf(item("IT-DEV-A", 22.5, LocalDateTime.now(), 1L));

        MvcResult result = mockMvc.perform(post("/ingest/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + credential.getAccessKey() + ":" + "0".repeat(64))
                        .content(body))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("4095");
    }

    @Test
    void disabledCredentialRejected() throws Exception {
        IngestCredential credential = createCredential(-2001L);
        credentialService.updateStatus(credential.getId(), 1, 1L);

        postSamples(credential, 401, item("IT-DEV-A", 22.5, LocalDateTime.now(), 1L));
    }

    @Test
    void expiredCredentialRejected() throws Exception {
        IngestCredentialCreateDTO createDTO = new IngestCredentialCreateDTO();
        createDTO.setGatewayName("过期网关");
        createDTO.setCloudRoomId(-2001L);
        createDTO.setExpireAt(LocalDateTime.now().minusMinutes(1));
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO, 1L);

        org.assertj.core.api.Assertions.assertThatCode(() ->
                        credentialService.authenticate(created.getAccessKey()))
                .isInstanceOf(com.example.platform.common.exception.BusinessException.class);

        // 过期凭证发真实请求 → 401
        String body = bodyOf(item("IT-DEV-A", 22.5, LocalDateTime.now(), 1L));
        String signature = GatewaySignUtils.signature(created.getSecret(), body);
        mockMvc.perform(post("/ingest/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + created.getAccessKey() + ":" + signature)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void heartbeatRefreshesLastSeen() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        String signature = GatewaySignUtils.signature(credential.getSecret(), "{\"ts\":\"x\"}");
        mockMvc.perform(post("/ingest/heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + credential.getAccessKey() + ":" + signature)
                        .content("{\"ts\":\"x\"}"))
                .andExpect(status().isOk());

        IngestCredential reloaded = credentialService.getById(credential.getId());
        assertThat(reloaded.getLastSeenAt()).isNotNull();
    }

    @Test
    void oversizeBatchRejectedByValidation() throws Exception {
        IngestCredential credential = createCredential(-2001L);
        StringBuilder items = new StringBuilder("[");
        for (int i = 0; i <= 200; i++) {
            if (i > 0) items.append(',');
            items.append("{\"deviceCode\":\"IT-DEV-A\",\"pointCode\":\"IT_TEST_TEMP\",\"value\":1,"
                    + "\"sampleTime\":\"2026-09-01T12:00:05\",\"seq\":").append(i).append('}');
        }
        items.append(']');
        String body = "{\"items\":" + items + "}";

        String signature = GatewaySignUtils.signature(credential.getSecret(), body);
        mockMvc.perform(post("/ingest/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + credential.getAccessKey() + ":" + signature)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyItemsRejectedByValidation() throws Exception {
        IngestCredential credential = createCredential(-2001L);
        postRaw(credential, "{\"items\":[]}", 400);
    }

    @Test
    void autoRegisterCredentialRegistersUnknownDevice() throws Exception {
        com.example.platform.ingest.dto.IngestCredentialCreateDTO createDTO =
                new com.example.platform.ingest.dto.IngestCredentialCreateDTO();
        createDTO.setGatewayName("auto-reg-gw");
        createDTO.setCloudRoomId(-2001L);
        createDTO.setAutoRegister(1);
        createDTO.setDefaultTypeId(4);
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO, 1L);

        String body = bodyOf(item("IT-AUTO-NEW", 25.0, LocalDateTime.now(), 1L));
        MvcResult result = mockMvc.perform(post("/ingest/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + created.getAccessKey() + ":"
                                + GatewaySignUtils.signature(created.getSecret(), body))
                        .content(body))
                .andExpect(status().isOk()).andReturn();

        assertThat(dataOf(result).get("accepted").asInt()).isEqualTo(1);
        mockMvc.perform(get("/devices/code-available")
                        .param("code", "IT-AUTO-NEW")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void autoRegisterWithoutCredentialDefaultTypeInfersFromPointCode() throws Exception {
        // 凭证未配默认类型：首包按点位编码推断（temperature → TEMP_HUMIDITY）
        com.example.platform.ingest.dto.IngestCredentialCreateDTO createDTO =
                new com.example.platform.ingest.dto.IngestCredentialCreateDTO();
        createDTO.setGatewayName("infer-gw");
        createDTO.setCloudRoomId(-2001L);
        createDTO.setAutoRegister(1);
        createDTO.setDefaultTypeId(null);
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO, 1L);

        String body = bodyOf(item("IT-INFER-NEW", 22.0, LocalDateTime.now(), 1L));
        MvcResult result = mockMvc.perform(post("/ingest/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + created.getAccessKey() + ":"
                                + GatewaySignUtils.signature(created.getSecret(), body))
                        .content(body))
                .andExpect(status().isOk()).andReturn();

        assertThat(dataOf(result).get("accepted").asInt()).isEqualTo(1);
        // temperature 推断为 TEMP_HUMIDITY(id=1, V3 种子)——但注意 V9 点位绑定在 type 1
        mockMvc.perform(get("/devices/code-available")
                        .param("code", "IT-INFER-NEW")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
    }

    @Test
    void inventoryRegistersDevicesWhenAutoRegisterEnabled() throws Exception {
        com.example.platform.ingest.dto.IngestCredentialCreateDTO createDTO =
                new com.example.platform.ingest.dto.IngestCredentialCreateDTO();
        createDTO.setGatewayName("inv-gw");
        createDTO.setCloudRoomId(-2001L);
        createDTO.setAutoRegister(1);
        createDTO.setDefaultTypeId(4);
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO, 1L);

        String body = "{\"devices\":["
                + "{\"deviceCode\":\"IT-INV-1\",\"deviceName\":\"inv1\",\"pointCodes\":[\"temperature\"]},"
                + "{\"deviceCode\":\"IT-DEV-A\",\"pointCodes\":[\"temperature\"]}]}";
        MvcResult result = mockMvc.perform(post("/ingest/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + created.getAccessKey() + ":"
                                + GatewaySignUtils.signature(created.getSecret(), body))
                        .content(body))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.get("registered").asInt()).isEqualTo(1);
        assertThat(data.get("exists").asInt()).isEqualTo(1);
        assertThat(data.get("unknown").asInt()).isZero();
    }

    @Test
    void inventoryReturnsUnknownWhenAutoRegisterDisabled() throws Exception {
        IngestCredential credential = createCredential(-2001L);

        String body = "{\"devices\":[{\"deviceCode\":\"IT-INV-2\",\"pointCodes\":[\"temperature\"]}]}";
        MvcResult result = mockMvc.perform(post("/ingest/inventory")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + credential.getAccessKey() + ":"
                                + GatewaySignUtils.signature(credential.getSecret(), body))
                        .content(body))
                .andExpect(status().isOk()).andReturn();

        JsonNode data = dataOf(result);
        assertThat(data.get("unknown").asInt()).isEqualTo(1);
        assertThat(data.get("registered").asInt()).isZero();
    }

    @Test
    void codeAvailableEndpointWorks() throws Exception {
        mockMvc.perform(get("/devices/code-available")
                        .param("code", "IT-DEV-A")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(false));
        mockMvc.perform(get("/devices/code-available")
                        .param("code", "IT-FRESH-CODE")
                        .header("Authorization", "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.available").value(true));
    }

    private String loginToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin123\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("token").asText();
    }

    // ===================== 辅助 =====================

    private IngestCredential createCredential(Long cloudRoomId) {
        IngestCredentialCreateDTO createDTO = new IngestCredentialCreateDTO();
        createDTO.setGatewayName("集成测试网关");
        createDTO.setCloudRoomId(cloudRoomId);
        IngestCredentialCreatedVO created = credentialService.createCredential(createDTO, 1L);

        IngestCredential credential = new IngestCredential();
        credential.setId(created.getId());
        credential.setAccessKey(created.getAccessKey());
        credential.setSecret(created.getSecret());
        credential.setCloudRoomId(cloudRoomId);
        return credential;
    }

    private record SampleItem(String deviceCode, String pointCode, double value, LocalDateTime time, long seq) {
    }

    private SampleItem item(String deviceCode, double value, LocalDateTime time, long seq) {
        return new SampleItem(deviceCode, "IT_TEST_TEMP", value, time, seq);
    }

    private SampleItem itemVoltage(String deviceCode, double value, LocalDateTime time, long seq) {
        return new SampleItem(deviceCode, "voltage_a", value, time, seq);
    }

    private String bodyOf(SampleItem... items) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < items.length; i++) {
            SampleItem it = items[i];
            if (i > 0) sb.append(',');
            sb.append("{\"deviceCode\":\"").append(it.deviceCode())
                    .append("\",\"pointCode\":\"").append(it.pointCode())
                    .append("\",\"value\":").append(it.value())
                    .append(",\"sampleTime\":\"").append(ISO.format(it.time()))
                    .append("\",\"seq\":").append(it.seq()).append('}');
        }
        return sb.append("]}").toString();
    }

    private MvcResult postSamples(IngestCredential credential, int expectedStatus, SampleItem... items)
            throws Exception {
        return postRaw(credential, bodyOf(items), expectedStatus);
    }

    private MvcResult postRaw(IngestCredential credential, String body, int expectedStatus) throws Exception {
        String signature = GatewaySignUtils.signature(credential.getSecret(), body);
        return mockMvc.perform(post("/ingest/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Gateway " + credential.getAccessKey() + ":" + signature)
                        .content(body))
                .andExpect(status().is(expectedStatus)).andReturn();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        return root.get("data");
    }
}
