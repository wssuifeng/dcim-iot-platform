package com.example.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizationCreationApplicationDTO {
    @NotBlank @Size(max = 100)
    private String orgName;
    @NotBlank @Size(max = 50)
    private String orgCode;
    @Size(max = 200)
    private String description;
}
