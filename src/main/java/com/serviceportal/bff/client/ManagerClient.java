package com.serviceportal.bff.client;

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
 *   - POST   /manager/flows                                       (YAML body)
 *   - GET    /manager/flows?page=&size=&sort=&status=             (lista paginada / ativos)
 *   - GET    /manager/flows/{flowId}/versions/{version}           (metadados)
 *   - PUT    /manager/flows/{flowId}/versions/{version}           (YAML body)
 *   - DELETE /manager/flows/{flowId}/versions/{version}           (soft-delete)
 *   - GET    /manager/flows/{flowId}/versions/{version}/yaml      (YAML cru)
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

    /**
     * Lista de fluxos. Quando {@code status=="active"}, o Manager retorna lista
     * compacta (sem `yamlContent`) e sem paginação — caso contrário, devolve
     * uma `Page` paginada.
     */
    public Object listFlows(int page, int size, String sort, String status) {
        return managerWebClient.get()
                .uri(uri -> {
                    var b = uri.path("/manager/flows")
                            .queryParam("page", page)
                            .queryParam("size", size);
                    if (sort != null && !sort.isBlank()) b.queryParam("sort", sort);
                    if (status != null && !status.isBlank()) b.queryParam("status", status);
                    return b.build();
                })
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    public Map<String, Object> getFlow(String flowId, String version) {
        return managerWebClient.get()
                .uri("/manager/flows/{flowId}/versions/{version}", flowId, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public String getFlowYaml(String flowId, String version) {
        return managerWebClient.get()
                .uri("/manager/flows/{flowId}/versions/{version}/yaml", flowId, version)
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

    public Map<String, Object> updateFlow(String flowId, String version, String yaml) {
        return managerWebClient.put()
                .uri("/manager/flows/{flowId}/versions/{version}", flowId, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue(yaml)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    public void deleteFlow(String flowId, String version) {
        managerWebClient.delete()
                .uri("/manager/flows/{flowId}/versions/{version}", flowId, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}
