package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MembershipRoleUpdateDTO {
    @NotBlank
    @Pattern(regexp = "^(ORG_ADMIN|OPERATOR|VIEWER)$")
    private String role;
}
