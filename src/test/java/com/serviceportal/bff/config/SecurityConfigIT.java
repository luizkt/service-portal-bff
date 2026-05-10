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

import com.serviceportal.bff.client.OrchestratorAuthService;
import com.serviceportal.bff.client.OrchestratorClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/jwks/",
        "bff.auth.issuer-uri=http://localhost:9999/issuer/",
        "bff.auth.client-id=test-spa",
        "bff.orchestrator.base-url=http://localhost:9998",
        "bff.orchestrator.username=u",
        "bff.orchestrator.password=p"
})
class SecurityConfigIT {

    @Autowired private MockMvc mvc;

    // Beans externos que dependem de infra real — substituídos por mocks
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private OrchestratorAuthService orchestratorAuthService;
    @MockBean private OrchestratorClient orchestratorClient;

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
}
