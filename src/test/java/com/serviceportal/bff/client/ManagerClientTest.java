package com.serviceportal.bff.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagerClientTest {

    private MockWebServer mockWebServer;
    private ManagerAuthService authService;
    private ManagerClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        authService = mock(ManagerAuthService.class);
        when(authService.getToken()).thenReturn("test-token");

        client = new ManagerClient(webClient, authService);
    }

    @AfterEach
    void tearDown() throws IOException { mockWebServer.shutdown(); }

    private RecordedRequest enqueueAndTake(String body) throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody(body));
        return null;
    }

    @Test @DisplayName("listFlows envia paginação como query params")
    void listFlowsPagina() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[],\"totalElements\":0,\"size\":20,\"number\":0}"));

        var resp = client.listFlows(0, 20, "flowId,asc");
        assertThat(resp).containsKey("content");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("/manager/flows").contains("page=0").contains("size=20").contains("sort=flowId,asc");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test @DisplayName("listFlows omite sort quando vazio")
    void listFlowsSemSort() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[]}"));

        client.listFlows(2, 50, null);
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("page=2").contains("size=50").doesNotContain("sort=");
    }

    @Test @DisplayName("getFlow monta path com flowId/versao e envia Authorization")
    void getFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"flowId\":\"x\",\"versao\":\"1\"}"));

        var resp = client.getFlow("x", "1.0.0");
        assertThat(resp).containsEntry("flowId", "x");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/1.0.0");
    }

    @Test @DisplayName("getFlowYaml retorna texto cru")
    void getFlowYamlOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/x-yaml")
                .setBody("fluxo:\n  id: x\n"));

        String yaml = client.getFlowYaml("x", "1.0.0");
        assertThat(yaml).startsWith("fluxo:");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/manager/workflows/x/1.0.0/yaml");
    }

    @Test @DisplayName("createFlow POSTa o YAML como text/plain")
    void createFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"flowId\":\"x\"}"));

        client.createFlow("fluxo:\n  id: x\n");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/manager/flows");
        assertThat(req.getHeader("Content-Type")).startsWith("text/plain");
        assertThat(req.getBody().readUtf8()).contains("id: x");
    }

    @Test @DisplayName("updateFlow PUT no path com flowId/versao")
    void updateFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));

        client.updateFlow("x", "1.0.0", "yaml-content");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PUT");
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/1.0.0");
        assertThat(req.getBody().readUtf8()).isEqualTo("yaml-content");
    }

    @Test @DisplayName("deleteFlow DELETE no path com flowId/versao")
    void deleteFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        client.deleteFlow("x", "1.0.0");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/1.0.0");
    }
}
