package com.example.platform.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalIdentityContractTest {

    @Test
    void sysUserExposesGlobalIdentityFields() {
        SysUser user = new SysUser();
        user.setEmail("user@example.com");
        user.setEmailVerified(true);
        user.setPlatformRole(null);

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getPlatformRole()).isNull();
    }
}
