package com.serviceportal.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

@Data
@Builder
public class MenuItemDto {
    private String id;
    private String label;
    private String icon;
    private String uiSchemaUrl;

    /** Grupos que podem ver este item. Vazio = público (sem restrição). Não enviado ao frontend. */
    @JsonIgnore
    @Singular
    private List<String> requiredGroups;
}
