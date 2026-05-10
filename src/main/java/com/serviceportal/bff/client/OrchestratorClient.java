package com.serviceportal.bff.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Cliente do orquestrador. Após o refactor para o service-portal-manager,
 * só sobra a chamada de **execução** do fluxo — todo CRUD migrou para o
 * {@link ManagerClient}.
 */
@Service
public class OrchestratorClient {

    private final WebClient orchestratorWebClient;
    private final OrchestratorAuthService authService;

    public OrchestratorClient(@Qualifier("orchestratorWebClient") WebClient orchestratorWebClient,
                              OrchestratorAuthService authService) {
        this.orchestratorWebClient = orchestratorWebClient;
        this.authService = authService;
    }

    private String authHeader() {
        return "Bearer " + authService.getToken();
    }

    public Map<String, Object> orchestrate(String version, String flowId, Map<String, Object> payload) {
        return orchestratorWebClient.post()
                .uri("/api/orchestrate/{version}/{flowId}", version, flowId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }
}
