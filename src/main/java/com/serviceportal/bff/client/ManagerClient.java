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

    // =====================================================================
    // Recursos modulares: integrations / contracts / validations
    //
    // Os três compartilham a mesma forma REST no Manager (lista paginada/ativa,
    // get por id+version, histórico de versões, create/update/delete via JSON).
    // Diferem apenas no segmento de path e no nome do campo de id. `version` é
    // inteiro (versionamento sequencial 1→2→3), diferente dos flows (SemVer).
    // =====================================================================

    private Object listResource(String resource, int page, int size, String sort, String status) {
        return managerWebClient.get()
                .uri(uri -> {
                    var b = uri.path("/manager/" + resource)
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

    private Map<String, Object> getResource(String resource, String id, int version) {
        return managerWebClient.get()
                .uri("/manager/{resource}/{id}/versions/{version}", resource, id, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    private Object listResourceVersions(String resource, String id, String status) {
        return managerWebClient.get()
                .uri(uri -> {
                    var b = uri.path("/manager/" + resource + "/" + id + "/versions");
                    if (status != null && !status.isBlank()) b.queryParam("status", status);
                    return b.build();
                })
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .bodyToMono(Object.class)
                .block();
    }

    private Map<String, Object> createResource(String resource, Object body) {
        return managerWebClient.post()
                .uri("/manager/{resource}", resource)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    private Map<String, Object> updateResource(String resource, String id, int version, Object body) {
        return managerWebClient.put()
                .uri("/manager/{resource}/{id}/versions/{version}", resource, id, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();
    }

    private void deleteResource(String resource, String id, int version) {
        managerWebClient.delete()
                .uri("/manager/{resource}/{id}/versions/{version}", resource, id, version)
                .header(HttpHeaders.AUTHORIZATION, authHeader())
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    // ---- integrations ----
    public Object listIntegrations(int page, int size, String sort, String status) {
        return listResource("integrations", page, size, sort, status);
    }
    public Map<String, Object> getIntegration(String integrationId, int version) {
        return getResource("integrations", integrationId, version);
    }
    public Object listIntegrationVersions(String integrationId, String status) {
        return listResourceVersions("integrations", integrationId, status);
    }
    public Map<String, Object> createIntegration(Object body) {
        return createResource("integrations", body);
    }
    public Map<String, Object> updateIntegration(String integrationId, int version, Object body) {
        return updateResource("integrations", integrationId, version, body);
    }
    public void deleteIntegration(String integrationId, int version) {
        deleteResource("integrations", integrationId, version);
    }

    // ---- contracts ----
    public Object listContracts(int page, int size, String sort, String status) {
        return listResource("contracts", page, size, sort, status);
    }
    public Map<String, Object> getContract(String contractId, int version) {
        return getResource("contracts", contractId, version);
    }
    public Object listContractVersions(String contractId, String status) {
        return listResourceVersions("contracts", contractId, status);
    }
    public Map<String, Object> createContract(Object body) {
        return createResource("contracts", body);
    }
    public Map<String, Object> updateContract(String contractId, int version, Object body) {
        return updateResource("contracts", contractId, version, body);
    }
    public void deleteContract(String contractId, int version) {
        deleteResource("contracts", contractId, version);
    }

    // ---- validations ----
    public Object listValidations(int page, int size, String sort, String status) {
        return listResource("validations", page, size, sort, status);
    }
    public Map<String, Object> getValidation(String validationId, int version) {
        return getResource("validations", validationId, version);
    }
    public Object listValidationVersions(String validationId, String status) {
        return listResourceVersions("validations", validationId, status);
    }
    public Map<String, Object> createValidation(Object body) {
        return createResource("validations", body);
    }
    public Map<String, Object> updateValidation(String validationId, int version, Object body) {
        return updateResource("validations", validationId, version, body);
    }
    public void deleteValidation(String validationId, int version) {
        deleteResource("validations", validationId, version);
    }
}
