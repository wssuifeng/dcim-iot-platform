package com.example.platform.entity;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserJsonContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passwordIsAcceptedFromRequestsButNeverSerialized() throws Exception {
        SysUser user = objectMapper.readValue("""
                {
                  "username": "operator01",
                  "password": "Admin123",
                  "nickname": "Operator",
                  "role": "OPERATOR"
                }
                """, SysUser.class);

        assertThat(user.getPassword()).isEqualTo("Admin123");

        String responseJson = objectMapper.writeValueAsString(user);
        assertThat(responseJson)
                .doesNotContain("password")
                .doesNotContain("Admin123");
    }
}
