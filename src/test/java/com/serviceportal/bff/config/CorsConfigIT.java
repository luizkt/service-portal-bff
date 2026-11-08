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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.serviceportal.bff.client.ManagerAuthService;
import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorAuthService;
import com.serviceportal.bff.client.OrchestratorClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
            "bff.auth.allowed-issuers=http://localhost:9999/issuer/",
            "bff.orchestrator.base-url=http://localhost:9998",
            "bff.orchestrator.username=u",
            "bff.orchestrator.password=p",
            "bff.manager.base-url=http://localhost:9997",
            "bff.manager.username=u",
            "bff.manager.password=p",
            "bff.cors.allowed-origins=https://app.example.com"
        })
class CorsConfigIT {

    @Autowired private MockMvc mvc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private OrchestratorAuthService orchestratorAuthService;
    @MockitoBean private OrchestratorClient orchestratorClient;
    @MockitoBean private ManagerAuthService managerAuthService;
    @MockitoBean private ManagerClient managerClient;

    @Test
    @DisplayName("preflight de origem permitida recebe os headers CORS")
    void allowedOriginPreflight() throws Exception {
        mvc.perform(
                        options("/bff/flows")
                                .header("Origin", "https://app.example.com")
                                .header("Access-Control-Request-Method", "GET")
                                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(
                        header().string("Access-Control-Allow-Origin", "https://app.example.com"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }

    @Test
    @DisplayName("preflight de origem não permitida não recebe headers CORS")
    void disallowedOriginPreflight() throws Exception {
        mvc.perform(
                        options("/bff/flows")
                                .header("Origin", "https://evil.example.org")
                                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("requisição simples de origem não permitida não recebe Allow-Origin")
    void disallowedOriginSimpleRequest() throws Exception {
        mvc.perform(get("/bff/health").header("Origin", "https://evil.example.org"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
