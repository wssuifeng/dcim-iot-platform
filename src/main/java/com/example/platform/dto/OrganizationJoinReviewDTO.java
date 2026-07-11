package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizationJoinReviewDTO {
    @NotBlank
    @Pattern(regexp = "^(ORG_ADMIN|OPERATOR|VIEWER)$")
    private String role;
    @Size(max = 500)
    private String note;
}
