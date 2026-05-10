package com.serviceportal.bff.controller;

import com.serviceportal.bff.dto.MenuItemDto;
import com.serviceportal.bff.dto.UiSchemaDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bff")
public class BffMenuController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<MenuItemDto>> menu() {
        return ResponseEntity.ok(List.of(
                MenuItemDto.builder()
                        .id("flow-manager")
                        .label("Gerenciador de Fluxos")
                        .icon("workflow")
                        .uiSchemaUrl("/bff/ui/flow-manager")
                        .build()
        ));
    }

    @GetMapping("/ui/{featureId}")
    public ResponseEntity<UiSchemaDto> uiSchema(@PathVariable String featureId) {
        return switch (featureId) {
            case "flow-manager" -> ResponseEntity.ok(
                    UiSchemaDto.builder()
                            .featureId("flow-manager")
                            .type("flow-manager")
                            .title("Gerenciador de Fluxos")
                            .build()
            );
            default -> ResponseEntity.notFound().build();
        };
    }
}
