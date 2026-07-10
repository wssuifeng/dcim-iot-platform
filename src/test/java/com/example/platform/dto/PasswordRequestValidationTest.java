package com.example.platform.dto;

import com.example.platform.common.group.CreateGroup;
import com.example.platform.entity.SysUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createUserAcceptsStrongPasswordAndRejectsWeakPassword() {
        SysUser user = new SysUser();
        user.setUsername("operator01");
        user.setRole("OPERATOR");
        user.setPassword("Admin123");

        assertThat(validator.validate(user, CreateGroup.class)).isEmpty();

        user.setPassword("password");
        Set<ConstraintViolation<SysUser>> violations =
                validator.validate(user, CreateGroup.class);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("password");
    }

    @Test
    void passwordResetAcceptsStrongPasswordAndRejectsWeakPassword() {
        PasswordResetDTO request = new PasswordResetDTO();
        request.setNewPassword("Changed123");

        assertThat(validator.validate(request)).isEmpty();

        request.setNewPassword("short");
        Set<ConstraintViolation<PasswordResetDTO>> violations = validator.validate(request);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("newPassword");
    }
}
