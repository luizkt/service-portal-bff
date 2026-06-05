package com.serviceportal.bff.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UiSchemaDto {
    private String featureId;
    private String type;
    private String title;
}
