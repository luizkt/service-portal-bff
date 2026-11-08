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
package com.serviceportal.bff.client;

import com.serviceportal.bff.config.BffProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Cliente do orquestrador. Após o refactor para o service-portal-manager, só sobra a chamada de
 * **execução** do fluxo — todo CRUD migrou para o {@link ManagerClient}.
 */
@Service
public class OrchestratorClient {

    private final WebClient orchestratorWebClient;
    private final OrchestratorAuthService authService;

    private final Duration deadline;

    @Autowired
    public OrchestratorClient(
            @Qualifier("orchestratorWebClient") WebClient orchestratorWebClient,
            OrchestratorAuthService authService,
            BffProperties props) {
        this.orchestratorWebClient = orchestratorWebClient;
        this.authService = authService;
        this.deadline = HttpTimeouts.deadline(props.getTimeoutMs());
    }

    public OrchestratorClient(
            @Qualifier("orchestratorWebClient") WebClient orchestratorWebClient,
            OrchestratorAuthService authService) {
        this.orchestratorWebClient = orchestratorWebClient;
        this.authService = authService;
        this.deadline = HttpTimeouts.deadline(new BffProperties().getTimeoutMs());
    }

    private String authHeader() {
        return "Bearer " + authService.getToken();
    }

    public Map<String, Object> execute(String flowId, String version, Map<String, Object> payload) {
        return post(
                "/api/v1/flows/{flowId}/versions/{version}/executions", flowId, version, payload);
    }

    public Map<String, Object> executeV2(
            String flowId, String version, Map<String, Object> payload) {
        return post(
                "/api/v2/flows/{flowId}/versions/{version}/executions", flowId, version, payload);
    }

    private Map<String, Object> post(
            String uriTemplate, String flowId, String version, Map<String, Object> payload) {
        return orchestratorWebClient
                .post()
                .uri(uriTemplate, flowId, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(deadline, HttpTimeouts.onTimeout(deadline))
                .block();
    }
}
