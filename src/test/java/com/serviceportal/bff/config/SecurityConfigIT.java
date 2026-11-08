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
package com.serviceportal.bff.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceportal.bff.client.ManagerAuthService;
import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorAuthService;
import com.serviceportal.bff.client.OrchestratorClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
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

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private OrchestratorAuthService orchestratorAuthService;
    @MockitoBean private OrchestratorClient orchestratorClient;
    @MockitoBean private ManagerAuthService managerAuthService;
    @MockitoBean private ManagerClient managerClient;

    // ─── endpoints públicos ────────────────────────────────────────────────

    @Test
    @DisplayName("/bff/auth/config é público e retorna 200 sem token")
    void authConfigPublico() throws Exception {
        mvc.perform(get("/bff/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuerUri").value("http://localhost:9999/issuer/"))
                .andExpect(jsonPath("$.clientId").value("test-spa"))
                .andExpect(jsonPath("$.scopes").isArray());
    }

    @Test
    @DisplayName("/bff/health é público e retorna 200 sem token")
    void healthPublico() throws Exception {
        mvc.perform(get("/bff/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v3/api-docs é público e retorna 200 sem token")
    void swaggerApiDocsPublico() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    // ─── autenticação requerida ────────────────────────────────────────────

    @Test
    @DisplayName("/bff/menu sem token retorna 401")
    void menuExigeToken() throws Exception {
        mvc.perform(get("/bff/menu")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/bff/flows sem token retorna 401")
    void flowsExigeToken() throws Exception {
        mvc.perform(get("/bff/flows")).andExpect(status().isUnauthorized());
    }

    // ─── autorização por grupo ────────────────────────────────────────────

    @Test
    @DisplayName("GET /bff/flows com grupo WORKFLOWS retorna 200")
    void flowsGrupoWorkflows() throws Exception {
        when(managerClient.listFlows(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of(), "totalElements", 0));

        mvc.perform(
                        get("/bff/flows")
                                .with(jwt().authorities(new SimpleGrantedAuthority("WORKFLOWS"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /bff/flows com grupo ADMIN retorna 200")
    void flowsGrupoAdmin() throws Exception {
        when(managerClient.listFlows(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of(), "totalElements", 0));

        mvc.perform(get("/bff/flows").with(jwt().authorities(new SimpleGrantedAuthority("ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /bff/flows com grupo RULES retorna 403")
    void flowsGrupoRulesBloqueado() throws Exception {
        mvc.perform(get("/bff/flows").with(jwt().authorities(new SimpleGrantedAuthority("RULES"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /bff/flows autenticado sem grupos retorna 403")
    void flowsSemGruposBloqueado() throws Exception {
        mvc.perform(get("/bff/flows").with(jwt())).andExpect(status().isForbidden());
    }

    // ─── S4: autorização dos recursos modulares ─────────────────────────────

    @Test
    @DisplayName("GET /bff/integrations: WORKFLOWS 200, RULES 403")
    void integrationsRead() throws Exception {
        when(managerClient.listIntegrations(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of()));
        mvc.perform(
                        get("/bff/integrations")
                                .with(jwt().authorities(new SimpleGrantedAuthority("WORKFLOWS"))))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/bff/integrations")
                                .with(jwt().authorities(new SimpleGrantedAuthority("RULES"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /bff/integrations: WORKFLOWS 403 (escrita só ADMIN)")
    void integrationsWriteOnlyAdmin() throws Exception {
        mvc.perform(
                        post("/bff/integrations")
                                .contentType("application/json")
                                .content("{}")
                                .with(jwt().authorities(new SimpleGrantedAuthority("WORKFLOWS"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /bff/contracts: RULES 200; POST: RULES 403, WORKFLOWS 201")
    void contractsAccess() throws Exception {
        when(managerClient.listContracts(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of()));
        when(managerClient.createContract(any()))
                .thenReturn(Map.of("contractId", "co", "version", 1));
        mvc.perform(
                        get("/bff/contracts")
                                .with(jwt().authorities(new SimpleGrantedAuthority("RULES"))))
                .andExpect(status().isOk());
        mvc.perform(
                        post("/bff/contracts")
                                .contentType("application/json")
                                .content("{}")
                                .with(jwt().authorities(new SimpleGrantedAuthority("RULES"))))
                .andExpect(status().isForbidden());
        mvc.perform(
                        post("/bff/contracts")
                                .contentType("application/json")
                                .content("{}")
                                .with(jwt().authorities(new SimpleGrantedAuthority("WORKFLOWS"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET /bff/validations: RULES 200, WORKFLOWS 403")
    void validationsRead() throws Exception {
        when(managerClient.listValidations(anyInt(), anyInt(), any(), any()))
                .thenReturn(Map.of("content", List.of()));
        mvc.perform(
                        get("/bff/validations")
                                .with(jwt().authorities(new SimpleGrantedAuthority("RULES"))))
                .andExpect(status().isOk());
        mvc.perform(
                        get("/bff/validations")
                                .with(jwt().authorities(new SimpleGrantedAuthority("WORKFLOWS"))))
                .andExpect(status().isForbidden());
    }
}
