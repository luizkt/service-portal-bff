/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.serviceportal.bff.controller;

import com.serviceportal.bff.dto.MenuItemDto;
import com.serviceportal.bff.dto.UiSchemaDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.tags.Tag(name = "Menu")
@RestController
@RequestMapping("/bff")
public class BffMenuController {

    /** Catálogo completo de itens — filtrado por grupo antes de enviar ao frontend. */
    private static final List<MenuItemDto> ALL_ITEMS =
            List.of(
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
                            .build());

    @Operation(summary = "Health check", description = "Verifica se o BFF está respondendo.")
    @ApiResponse(
            responseCode = "200",
            description = "BFF ativo",
            content = @Content(examples = @ExampleObject(value = "{\"status\":\"UP\"}")))
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @Operation(
            summary = "Itens de menu filtrados por grupo",
            description =
                    "Retorna apenas os itens que o usuário autenticado pode ver (Server Driven UI)."
                            + " Requer Bearer JWT com claim 'groups'.")
    @ApiResponse(
            responseCode = "200",
            description = "Lista de itens visíveis ao usuário",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            name = "ADMIN",
                                            value =
                                                    "[{\"id\":\"flow-manager\",\"label\":\"Gerenciador de Fluxos\",\"icon\":\"workflow\",\"uiSchemaUrl\":\"/bff/features/flow-manager/ui-schema\"},"
                                                            + "{\"id\":\"integration-manager\",\"label\":\"Integrações\",\"icon\":\"integration\",\"uiSchemaUrl\":\"/bff/features/integration-manager/ui-schema\"},"
                                                            + "{\"id\":\"contract-manager\",\"label\":\"Contratos\",\"icon\":\"contract\",\"uiSchemaUrl\":\"/bff/features/contract-manager/ui-schema\"},"
                                                            + "{\"id\":\"validation-manager\",\"label\":\"Validações\",\"icon\":\"validation\",\"uiSchemaUrl\":\"/bff/features/validation-manager/ui-schema\"}]")))
    @GetMapping("/menu")
    public ResponseEntity<List<MenuItemDto>> menu(@AuthenticationPrincipal Jwt jwt) {
        List<String> userGroups = jwt != null ? jwt.getClaimAsStringList("groups") : List.of();
        if (userGroups == null) userGroups = List.of();

        final List<String> groups = userGroups;
        List<MenuItemDto> visible =
                ALL_ITEMS.stream()
                        .filter(
                                item ->
                                        item.getRequiredGroups().isEmpty()
                                                || containsAny(item.getRequiredGroups(), groups))
                        .toList();

        return ResponseEntity.ok(visible);
    }

    @Operation(
            summary = "UI Schema de uma feature",
            description =
                    "Retorna o schema de componente React que o frontend deve renderizar para a feature."
                            + " Segue o padrão Server Driven UI.")
    @ApiResponse(
            responseCode = "200",
            description = "UI Schema da feature",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"featureId\":\"flow-manager\",\"type\":\"flow-manager\",\"title\":\"Gerenciador de Fluxos\"}")))
    @ApiResponse(responseCode = "404", description = "Feature não encontrada")
    @GetMapping("/features/{featureId}/ui-schema")
    public ResponseEntity<UiSchemaDto> uiSchema(
            @Parameter(description = "Identificador da feature", example = "flow-manager")
                    @PathVariable
                    String featureId) {
        return switch (featureId) {
            case "flow-manager" ->
                    ResponseEntity.ok(
                            UiSchemaDto.builder()
                                    .featureId("flow-manager")
                                    .type("flow-manager")
                                    .title("Gerenciador de Fluxos")
                                    .build());
            case "integration-manager" ->
                    ResponseEntity.ok(
                            UiSchemaDto.builder()
                                    .featureId("integration-manager")
                                    .type("integration-manager")
                                    .title("Gerenciamento de Integrações")
                                    .build());
            case "contract-manager" ->
                    ResponseEntity.ok(
                            UiSchemaDto.builder()
                                    .featureId("contract-manager")
                                    .type("contract-manager")
                                    .title("Gerenciamento de Contratos")
                                    .build());
            case "validation-manager" ->
                    ResponseEntity.ok(
                            UiSchemaDto.builder()
                                    .featureId("validation-manager")
                                    .type("validation-manager")
                                    .title("Gerenciamento de Validações")
                                    .build());
            default -> ResponseEntity.notFound().build();
        };
    }

    private static boolean containsAny(Collection<String> required, Collection<String> actual) {
        return required.stream().anyMatch(actual::contains);
    }
}
