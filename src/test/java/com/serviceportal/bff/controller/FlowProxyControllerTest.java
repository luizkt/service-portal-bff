package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowProxyControllerTest {

    private ManagerClient managerClient;
    private OrchestratorClient orchestratorClient;
    private FlowProxyController controller;

    @BeforeEach
    void setUp() {
        managerClient = mock(ManagerClient.class);
        orchestratorClient = mock(OrchestratorClient.class);
        controller = new FlowProxyController(managerClient, orchestratorClient);
    }

    @Test @DisplayName("listFlows delega para Manager com pagination params")
    void listFlows() {
        when(managerClient.listFlows(0, 20, "flowId,asc"))
                .thenReturn(Map.of("content", java.util.List.of(), "totalElements", 0));

        var resp = controller.listFlows(0, 20, "flowId,asc");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(managerClient).listFlows(0, 20, "flowId,asc");
    }

    @Test @DisplayName("getFlow 200 delega para Manager")
    void getFlowOk() {
        when(managerClient.getFlow("x", "1.0")).thenReturn(Map.of("flowId", "x"));
        ResponseEntity<Map<String, Object>> resp = controller.getFlow("x", "1.0");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @DisplayName("getFlow mapeia 404 do Manager para 404")
    void getFlowNotFound() {
        when(managerClient.getFlow("x", "1.0")).thenThrow(notFoundException());
        var resp = controller.getFlow("x", "1.0");
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
    }

    @Test @DisplayName("getFlowYaml retorna content-type application/x-yaml")
    void getFlowYamlOk() {
        when(managerClient.getFlowYaml("x", "1.0")).thenReturn("fluxo:\n  id: x\n");
        var resp = controller.getFlowYaml("x", "1.0");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType().toString()).startsWith("application/x-yaml");
    }

    @Test @DisplayName("getFlowYaml 404 quando Manager devolve 404")
    void getFlowYamlNotFound() {
        when(managerClient.getFlowYaml("x", "1.0")).thenThrow(notFoundException());
        assertThat(controller.getFlowYaml("x", "1.0").getStatusCode().value()).isEqualTo(404);
    }

    @Test @DisplayName("createFlow devolve 201")
    void createOk() {
        when(managerClient.createFlow("yaml")).thenReturn(Map.of("flowId", "x"));
        var resp = controller.createFlow("yaml");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test @DisplayName("updateFlow delega ao Manager")
    void updateOk() {
        when(managerClient.updateFlow("x", "1.0", "y")).thenReturn(Map.of());
        var resp = controller.updateFlow("x", "1.0", "y");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
    }

    @Test @DisplayName("updateFlow 404 quando Manager 404")
    void updateNotFound() {
        when(managerClient.updateFlow(any(), any(), any())).thenThrow(notFoundException());
        assertThat(controller.updateFlow("x", "1", "y").getStatusCode().value()).isEqualTo(404);
    }

    @Test @DisplayName("deleteFlow 204")
    void deleteOk() {
        var resp = controller.deleteFlow("x", "1.0");
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
        verify(managerClient).deleteFlow("x", "1.0");
    }

    @Test @DisplayName("deleteFlow 404 quando Manager 404")
    void deleteNotFound() {
        doThrow(notFoundException()).when(managerClient).deleteFlow("x", "1.0");
        assertThat(controller.deleteFlow("x", "1.0").getStatusCode().value()).isEqualTo(404);
    }

    @Test @DisplayName("orchestrate delega para OrchestratorClient")
    void orchestrate() {
        when(orchestratorClient.orchestrate(eq("v1"), eq("x"), any()))
                .thenReturn(Map.of("status", "SUCCESS"));
        var resp = controller.orchestrate("v1", "x", Map.of("k", "v"));
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(orchestratorClient).orchestrate("v1", "x", Map.of("k", "v"));
    }

    private WebClientResponseException.NotFound notFoundException() {
        return (WebClientResponseException.NotFound) WebClientResponseException
                .create(404, "Not Found", null, null, null);
    }
}
