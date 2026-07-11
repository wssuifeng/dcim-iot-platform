package com.example.platform.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizationJoinApplicationDTO {
    @Size(max = 500)
    private String note;
}
