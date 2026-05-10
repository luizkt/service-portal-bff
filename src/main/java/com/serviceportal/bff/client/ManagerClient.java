package com.serviceportal.bff.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Cliente HTTP para o service-portal-manager — todo CRUD de workflows
 * (POST/GET/PUT/DELETE) acontece nele após a refatoração do BFF.
 *
 * Endpoints REST do Manager (vide service-portal-manager/README.md):
 *   - POST   /manager/flows                            (YAML body)
 *   - GET    /manager/flows?page=&size=&sort=          (lista paginada, sem yamlContent)
 *   - GET    /manager/flows/{flowId}/{versao}          (metadados)
 *   - PUT    /manager/flows/{flowId}/{versao}          (YAML body)
 *   - DELETE /manager/flows/{flowId}/{versao}          (soft-delete)
 *   - GET    /manager/workflows/{flowId}/{versao}/yaml (YAML cru)
 */
@Service
public class ManagerClient {

    private final WebClient managerWebClient;
    private final ManagerAuthService authService;

    public ManagerClient(@Qualifier("managerWebClient") WebClient managerWebClient,
                         ManagerAuthService authService) {
        this.managerWebClient = managerWebClient;
        this.authService = authService;
    }

    private String authHeader() {
        return "Bearer " + authService.getToken();
    }

    /** Lista paginada de fluxos. Retorna o objeto Page raw (Spring Data: content[], totalElements, etc.). */
    public Map<String, Object> listFlows(int page, int size, String sort) {
        WebClient.RequestHeadersUriSpec<?> spec = managerWebClient.get();
        return spec.uri(uri -> {
                    var b = uri.path("/manager/flows")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (sort != null && !sort.isBlank()) b.queryParam("sort", sort);
                    return b.build();
                })
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public Map<String, Object> getFlow(String flowId, String versao) {
        return managerWebClient.get()
                .uri("/manager/flows/{flowId}/{versao}", flowId, versao)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public String getFlowYaml(String flowId, String versao) {
        return managerWebClient.get()
                .uri("/manager/workflows/{flowId}/{versao}/yaml", flowId, versao)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public Map<String, Object> createFlow(String yaml) {
        return managerWebClient.post()
                .uri("/manager/flows")
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(yaml)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public Map<String, Object> updateFlow(String flowId, String versao, String yaml) {
        return managerWebClient.put()
                .uri("/manager/flows/{flowId}/{versao}", flowId, versao)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(yaml)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public void deleteFlow(String flowId, String versao) {
        managerWebClient.delete()
                .uri("/manager/flows/{flowId}/{versao}", flowId, versao)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
