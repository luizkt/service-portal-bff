package com.serviceportal.bff.controller;

import com.serviceportal.bff.dto.MenuItemDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test @DisplayName("ADMIN recebe flow-manager")
    void adminRecebeFlowManager() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of("ADMIN")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getId()).isEqualTo("flow-manager");
    }

    @Test @DisplayName("WORKFLOWS recebe flow-manager")
    void workflowsRecebeFlowManager() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of("WORKFLOWS")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).getId()).isEqualTo("flow-manager");
    }

    @Test @DisplayName("RULES recebe lista vazia")
    void rulesRecebeListaVazia() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of("RULES")));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test @DisplayName("Sem grupo recebe lista vazia")
    void semGrupoRecebeListaVazia() {
        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtWithGroups(List.of()));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test @DisplayName("JWT sem claim groups recebe lista vazia")
    void semClaimGroupsRecebeListaVazia() {
        Jwt jwtSemGroups = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claims(c -> c.putAll(Map.of("sub", "user1")))
                .build();

        ResponseEntity<List<MenuItemDto>> resp = controller.menu(jwtSemGroups);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEmpty();
    }

    @Test @DisplayName("requiredGroups nao e serializado no JSON do item")
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
        assertThat(field.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonIgnore.class)).isTrue();
    }
}
