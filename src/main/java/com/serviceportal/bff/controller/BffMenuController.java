package com.serviceportal.bff.controller;

import com.serviceportal.bff.dto.MenuItemDto;
import com.serviceportal.bff.dto.UiSchemaDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bff")
public class BffMenuController {

    /** Catálogo completo de itens — filtrado por grupo antes de enviar ao frontend. */
    private static final List<MenuItemDto> ALL_ITEMS = List.of(
            MenuItemDto.builder()
                    .id("flow-manager")
                    .label("Gerenciador de Fluxos")
                    .icon("workflow")
                    .uiSchemaUrl("/bff/features/flow-manager/ui-schema")
                    .requiredGroup("ADMIN")
                    .requiredGroup("WORKFLOWS")
                    .build(),
            MenuItemDto.builder()
                    .id("integration-manager")
                    .label("Integrações")
                    .icon("integration")
                    .uiSchemaUrl("/bff/features/integration-manager/ui-schema")
                    .requiredGroup("ADMIN")
                    .requiredGroup("WORKFLOWS")
                    .build(),
            MenuItemDto.builder()
                    .id("contract-manager")
                    .label("Contratos")
                    .icon("contract")
                    .uiSchemaUrl("/bff/features/contract-manager/ui-schema")
                    .requiredGroup("ADMIN")
                    .requiredGroup("WORKFLOWS")
                    .requiredGroup("RULES")
                    .build(),
            MenuItemDto.builder()
                    .id("validation-manager")
                    .label("Validações")
                    .icon("validation")
                    .uiSchemaUrl("/bff/features/validation-manager/ui-schema")
                    .requiredGroup("ADMIN")
                    .requiredGroup("RULES")
                    .build()
    );

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/menu")
    public ResponseEntity<List<MenuItemDto>> menu(@AuthenticationPrincipal Jwt jwt) {
        List<String> userGroups = jwt != null ? jwt.getClaimAsStringList("groups") : List.of();
        if (userGroups == null) userGroups = List.of();

        final List<String> groups = userGroups;
        List<MenuItemDto> visible = ALL_ITEMS.stream()
                .filter(item -> item.getRequiredGroups().isEmpty()
                        || containsAny(item.getRequiredGroups(), groups))
                .toList();

        return ResponseEntity.ok(visible);
    }

    /**
     * UI schema é sub-recurso do feature — REST-shape.
     * Substitui o antigo {@code GET /bff/ui/{featureId}}.
     */
    @GetMapping("/features/{featureId}/ui-schema")
    public ResponseEntity<UiSchemaDto> uiSchema(@PathVariable String featureId) {
        return switch (featureId) {
            case "flow-manager" -> ResponseEntity.ok(
                    UiSchemaDto.builder()
                            .featureId("flow-manager")
                            .type("flow-manager")
                            .title("Gerenciador de Fluxos")
                            .build()
            );
            case "integration-manager" -> ResponseEntity.ok(
                    UiSchemaDto.builder()
                            .featureId("integration-manager")
                            .type("integration-manager")
                            .title("Gerenciamento de Integrações")
                            .build()
            );
            case "contract-manager" -> ResponseEntity.ok(
                    UiSchemaDto.builder()
                            .featureId("contract-manager")
                            .type("contract-manager")
                            .title("Gerenciamento de Contratos")
                            .build()
            );
            case "validation-manager" -> ResponseEntity.ok(
                    UiSchemaDto.builder()
                            .featureId("validation-manager")
                            .type("validation-manager")
                            .title("Gerenciamento de Validações")
                            .build()
            );
            default -> ResponseEntity.notFound().build();
        };
    }

    private static boolean containsAny(Collection<String> required, Collection<String> actual) {
        return required.stream().anyMatch(actual::contains);
    }
}
