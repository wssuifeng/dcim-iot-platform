package com.example.platform.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrganizationApplicationReviewDTO {
    @Size(max = 500)
    private String note;
}
