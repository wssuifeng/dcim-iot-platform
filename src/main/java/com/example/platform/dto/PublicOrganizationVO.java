package com.example.platform.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PublicOrganizationVO {
    private Long id;
    private String orgName;
    private String orgCode;
    private boolean joinApplicationEnabled;
}
