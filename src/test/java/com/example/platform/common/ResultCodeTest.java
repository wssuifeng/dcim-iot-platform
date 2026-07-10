package com.example.platform.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ResultCodeTest {

    @Test
    void stableErrorCodesCoverStage4Prefixes() {
        Set<String> stableCodes = Arrays.stream(ResultCode.StableError.values())
                .map(ResultCode.StableError::getCode)
                .collect(Collectors.toSet());

        assertThat(stableCodes)
                .contains(
                        ResultCode.AUTH_UNAUTHORIZED,
                        ResultCode.AUTH_INVALID_CREDENTIALS,
                        ResultCode.AUTHZ_FORBIDDEN,
                        ResultCode.SCOPE_ACCESS_DENIED,
                        ResultCode.VALIDATION_INVALID_PARAMETER,
                        ResultCode.STATE_INVALID_TRANSITION,
                        ResultCode.SIM_WRITE_DISABLED,
                        ResultCode.RATE_LIMIT_EXCEEDED,
                        ResultCode.AUDIT_WRITE_FAILED,
                        ResultCode.WS_UNAUTHORIZED,
                        ResultCode.WS_ORIGIN_NOT_ALLOWED,
                        ResultCode.FEATURE_SWITCH_DISABLED,
                        ResultCode.SUPPLY_UNSAFE_CONFIG,
                        ResultCode.SYSTEM_INTERNAL_ERROR);
    }

    @Test
    void stableErrorMapsToLegacyNumericCode() {
        assertThat(ResultCode.legacyCodeOf(ResultCode.AUTH_UNAUTHORIZED))
                .isEqualTo(ResultCode.UNAUTHORIZED);
        assertThat(ResultCode.legacyCodeOf(ResultCode.SCOPE_ACCESS_DENIED))
                .isEqualTo(ResultCode.DATA_SCOPE_ACCESS_DENIED);
        assertThat(ResultCode.legacyCodeOf(ResultCode.STATE_INVALID_TRANSITION))
                .isEqualTo(ResultCode.ALARM_STATE_INVALID_TRANSITION);
        assertThat(ResultCode.legacyCodeOf(ResultCode.SIM_WRITE_DISABLED))
                .isEqualTo(ResultCode.SIMULATION_WRITE_DISABLED);
        assertThat(ResultCode.legacyCodeOf(ResultCode.SUPPLY_UNSAFE_CONFIG))
                .isEqualTo(ResultCode.SUPPLY_CONFIG_UNSAFE);
    }

    @Test
    void unknownStableErrorFallsBackToInternalServerError() {
        assertThat(ResultCode.findStableError("UNKNOWN_ERROR")).isEmpty();
        assertThat(ResultCode.legacyCodeOf("UNKNOWN_ERROR"))
                .isEqualTo(ResultCode.INTERNAL_SERVER_ERROR);
    }
}
