package com.serviceportal.bff.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MenuItemDto {
    private String id;
    private String label;
    private String icon;
    private String uiSchemaUrl;
}
