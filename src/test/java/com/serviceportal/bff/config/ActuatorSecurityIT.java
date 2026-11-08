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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceportal.bff.client.ManagerAuthService;
import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorAuthService;
import com.serviceportal.bff.client.OrchestratorClient;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureObservability
@AutoConfigureMockMvc
@TestPropertySource(
        properties = {
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:9999/jwks/",
            "bff.auth.issuer-uri=http://localhost:9999/issuer/",
            "bff.auth.allowed-issuers=http://localhost:9999/issuer/",
            "bff.orchestrator.base-url=http://localhost:9998",
            "bff.orchestrator.username=u",
            "bff.orchestrator.password=p",
            "bff.manager.base-url=http://localhost:9997",
            "bff.manager.username=u",
            "bff.manager.password=p",
            "bff.security.actuator.username=ops",
            "bff.security.actuator.password=s3cret-actuator"
        })
class ActuatorSecurityIT {

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private OrchestratorAuthService orchestratorAuthService;
    @MockitoBean private OrchestratorClient orchestratorClient;
    @MockitoBean private ManagerAuthService managerAuthService;
    @MockitoBean private ManagerClient managerClient;

    @Test
    @DisplayName("health e prometheus são públicos")
    void publicEndpoints() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("env e loggers exigem autenticação")
    void protectedEndpointsRejectAnonymous() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/loggers")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("heapdump não é exposto nem com credenciais")
    void heapdumpNotExposed() throws Exception {
        mvc.perform(get("/actuator/heapdump").with(httpBasic("ops", "s3cret-actuator")))
                .andExpect(
                        result ->
                                assertThat(result.getResponse().getStatus())
                                        .as("heapdump must not be served")
                                        .isNotEqualTo(200));
    }

    @Test
    @DisplayName("usuário ACTUATOR com Basic lê env e loggers")
    void basicCredentialsOpenProtectedEndpoints() throws Exception {
        mvc.perform(get("/actuator/env").with(httpBasic("ops", "s3cret-actuator")))
                .andExpect(status().isOk());
        mvc.perform(get("/actuator/loggers").with(httpBasic("ops", "s3cret-actuator")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("credenciais erradas retornam 401")
    void wrongCredentials() throws Exception {
        mvc.perform(get("/actuator/env").with(httpBasic("ops", "wrong")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env").with(httpBasic("admin", "admin")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("um Bearer JWT válido do Authentik não abre o actuator")
    void bearerDoesNotOpenActuator() throws Exception {
        Jwt jwt =
                Jwt.withTokenValue("tok")
                        .header("alg", "none")
                        .claim("iss", "http://localhost:9999/issuer/")
                        .claim("groups", List.of("ADMIN"))
                        .build();
        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        mvc.perform(get("/actuator/env").header("Authorization", "Bearer tok"))
                .andExpect(status().isUnauthorized());
    }
}
