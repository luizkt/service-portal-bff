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

import static org.assertj.core.api.Assertions.assertThat;

import com.serviceportal.bff.dto.MenuItemDto;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

class BffMenuControllerTest {

    private final BffMenuController controller = new BffMenuController();

    private Jwt jwtWithGroups(List<String> groups) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("groups", groups)
                .build();
    }

    private static List<String> ids(ResponseEntity<List<MenuItemDto>> resp) {
        return resp.getBody().stream().map(MenuItemDto::getId).toList();
    }

    @Test
    @DisplayName("ADMIN recebe os 4 itens")
    void adminRecebeTodos() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of("ADMIN")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(resp))
                .containsExactlyInAnyOrder(
                        "flow-manager",
                        "integration-manager",
                        "contract-manager",
                        "validation-manager");
    }

    @Test
    @DisplayName("WORKFLOWS recebe flow/integration/contract (sem validation)")
    void workflowsRecebeTres() {
        ResponseEntity<List<MenuItemDto>> resp =
                controller.menu(jwtWithGroups(List.of("WORKFLOWS")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(resp))
                .containsExactlyInAnyOrder(
                        "flow-manager", "integration-manager", "contract-manager");
    }

    @Test
    @DisplayName("RULES recebe contract e validation (sem flow/integration)")
    void rulesRecebeContractEValidation() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of("RULES")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ids(resp)).containsExactlyInAnyOrder("contract-manager", "validation-manager");
    }

    @Test
    @DisplayName("Sem grupo recebe lista vazia")
    void semGrupoRecebeListaVazia() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of()));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    @DisplayName("JWT sem claim groups recebe lista vazia")
    void semClaimGroupsRecebeListaVazia() {
        Jwt jwtSemGroups =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(3600))
                        .claims(c -> c.putAll(Map.of("sub", "user1")))
                        .build();

        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtSemGroups);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test
    @DisplayName("requiredGroups nao e serializado no JSON do item")
    void requiredGroupsNaoESerializado() {
        com.fasterxml.jackson.annotation.JsonIgnore annotation =
                MenuItemDto.class.getDeclaredFields()[0].getAnnotation(
                        com.fasterxml.jackson.annotation.JsonIgnore.class);
        // Verificação via reflexão de que @JsonIgnore está em requiredGroups
        java.lang.reflect.Field field;
        try {
            field = MenuItemDto.class.getDeclaredField("requiredGroups");
        } catch (NoSuchFieldException e) {
            throw new AssertionError("Campo requiredGroups não encontrado em MenuItemDto");
        }
        assertThat(field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonIgnore.class))
                .isTrue();
    }

    @Test
    @DisplayName("uiSchema retorna schema para cada feature conhecida")
    void uiSchemaFeaturesConhecidas() {
        for (String feature :
                List.of(
                        "flow-manager",
                        "integration-manager",
                        "contract-manager",
                        "validation-manager")) {
            var resp = controller.uiSchema(feature);
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody().getType()).isEqualTo(feature);
            assertThat(resp.getBody().getFeatureId()).isEqualTo(feature);
            assertThat(resp.getBody().getTitle()).isNotBlank();
        }
    }

    @Test
    @DisplayName("uiSchema retorna 404 para feature desconhecida")
    void uiSchemaDesconhecida() {
        var resp = controller.uiSchema("nope");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("health retorna UP")
    void healthUp() {
        assertThat(controller.health().getBody()).containsEntry("status", "UP");
    }
}
