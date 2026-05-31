package com.serviceportal.bff.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.serviceportal.bff.client.ManagerAuthService;
import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorAuthService;
import com.serviceportal.bff.client.OrchestratorClient;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/jwks/",
        "bff.auth.issuer-uri=http://localhost:9999/issuer/",
        "bff.auth.allowed-issuers=http://localhost:9999/issuer/,http://localhost:9999/m2m-issuer/",
        "bff.auth.client-id=test-spa",
        "bff.orchestrator.base-url=http://localhost:9998",
        "bff.orchestrator.username=u",
        "bff.orchestrator.password=p",
        "bff.manager.base-url=http://localhost:9997",
        "bff.manager.username=u",
        "bff.manager.password=p"
})
class SecurityConfigIT {

    @Autowired private MockMvc mvc;

    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private OrchestratorAuthService orchestratorAuthService;
    @MockBean private OrchestratorClient orchestratorClient;
    @MockBean private ManagerAuthService managerAuthService;
    @MockBean private ManagerClient managerClient;

    // ─── endpoints públicos ────────────────────────────────────────────────

    @Test @DisplayName("/bff/auth/config é público e retorna 200 sem token")
    void authConfigPublico() throws Exception {
        mvc.perform(get("/bff/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuerUri").value("http://localhost:9999/issuer/"))
                .andExpect(jsonPath("$.clientId").value("test-spa"))
                .andExpect(jsonPath("$.scopes").isArray());
    }

    @Test @DisplayName("/bff/health é público e retorna 200 sem token")
    void healthPublico() throws Exception {
        mvc.perform(get("/bff/health"))
                .andExpect(status().isOk());
    }

    // ─── autenticação requerida ────────────────────────────────────────────

    @Test @DisplayName("/bff/menu sem token retorna 401")
    void menuExigeToken() throws Exception {
        mvc.perform(get("/bff/menu"))
                .andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("/bff/flows sem token retorna 401")
    void flowsExigeToken() throws Exception {
        mvc.perform(get("/bff/flows"))
                .andExpect(status().isUnauthorized());
    }

    // ─── autorização por grupo ────────────────────────────────────────────

    @Test @DisplayName("GET /bff/flows com grupo WORKFLOWS retorna 200")
    void flowsGrupoWorkflows() throws Exception {
        when(managerClient.listFlows(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of(), "totalElements", 0));

        mvc.perform(get("/bff/flows")
                        .with(jwt().authorities(new SimpleGrantedAuthority("WORKFLOWS"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /bff/flows com grupo ADMIN retorna 200")
    void flowsGrupoAdmin() throws Exception {
        when(managerClient.listFlows(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of(), "totalElements", 0));

        mvc.perform(get("/bff/flows")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /bff/flows com grupo RULES retorna 403")
    void flowsGrupoRulesBloqueado() throws Exception {
        mvc.perform(get("/bff/flows")
                        .with(jwt().authorities(new SimpleGrantedAuthority("RULES"))))
                .andExpect(status().isForbidden());
    }

    @Test @DisplayName("GET /bff/flows autenticado sem grupos retorna 403")
    void flowsSemGruposBloqueado() throws Exception {
        mvc.perform(get("/bff/flows")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }
}
