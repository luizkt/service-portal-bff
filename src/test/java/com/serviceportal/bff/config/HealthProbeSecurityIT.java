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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceportal.bff.client.ManagerAuthService;
import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorAuthService;
import com.serviceportal.bff.client.OrchestratorClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Garante que o HEALTHCHECK da imagem (liveness anônimo) convive com o actuator protegido. */
@SpringBootTest
@AutoConfigureMetrics
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
            "bff.security.actuator.password=s3cret-actuator",
            "management.endpoint.health.probes.enabled=true"
        })
class HealthProbeSecurityIT {

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private OrchestratorAuthService orchestratorAuthService;
    @MockitoBean private OrchestratorClient orchestratorClient;
    @MockitoBean private ManagerAuthService managerAuthService;
    @MockitoBean private ManagerClient managerClient;

    @Test
    @DisplayName("liveness é público (HEALTHCHECK) e env continua exigindo credenciais")
    void livenessPublicEnvProtected() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
