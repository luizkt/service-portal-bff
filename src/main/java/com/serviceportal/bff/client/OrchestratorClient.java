package com.serviceportal.bff.client;

import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrchestratorClient {

    private final WebClient orchestratorWebClient;
    private final OrchestratorAuthService authService;

    private String authHeader() {
        return "Bearer " + authService.getToken();
    }

    public List<Map<String, Object>> listFlows() {
        return orchestratorWebClient.get()
                .uri("/api/flows")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                .block();
    }

    public Map<String, Object> getFlow(String flowId) {
        return orchestratorWebClient.get()
                .uri("/api/flows/{flowId}", flowId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public Map<String, Object> createFlow(String yaml) {
        return orchestratorWebClient.post()
                .uri("/api/flows")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(yaml)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public Map<String, Object> updateFlow(String flowId, String yaml) {
        return orchestratorWebClient.put()
                .uri("/api/flows/{flowId}", flowId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(yaml)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public void deleteFlow(String flowId) {
        orchestratorWebClient.delete()
                .uri("/api/flows/{flowId}", flowId)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .toBodilessEntity()
                .block();
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
