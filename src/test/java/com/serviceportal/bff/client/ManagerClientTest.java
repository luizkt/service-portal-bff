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

    @Test @DisplayName("listFlows sends pagination as query params")
    void listFlowsPaginated() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[],\"totalElements\":0,\"size\":20,\"number\":0}"));

        var resp = client.listFlows(0, 20, "flowId,asc", null);
        assertThat(resp).containsKey("content");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("/manager/flows").contains("page=0").contains("size=20").contains("sort=flowId,asc");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test @DisplayName("listFlows omits sort when blank")
    void listFlowsNoSort() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"content\":[]}"));

        client.listFlows(2, 50, null, null);
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("page=2").contains("size=50").doesNotContain("sort=");
    }

    @Test @DisplayName("listFlows forwards status=active as query param")
    void listFlowsStatus() throws InterruptedException {
        // Body is an empty map ({}) so deserialization to Map<String,Object> succeeds.
        // The intent of this test is only to validate the request shape.
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));
        client.listFlows(0, 20, null, "active");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).contains("status=active");
    }

    @Test @DisplayName("getFlow builds /manager/flows/{id}/versions/{v} path")
    void getFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"flowId\":\"x\",\"version\":\"1\"}"));

        var resp = client.getFlow("x", "1.0.0");
        assertThat(resp).containsEntry("flowId", "x");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/versions/1.0.0");
    }

    @Test @DisplayName("getFlowYaml returns raw text body")
    void getFlowYamlOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/x-yaml")
                .setBody("flow:\n  id: x\n"));

        String yaml = client.getFlowYaml("x", "1.0.0");
        assertThat(yaml).startsWith("flow:");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/versions/1.0.0/yaml");
    }

    @Test @DisplayName("createFlow POSTs YAML as text/plain")
    void createFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"flowId\":\"x\"}"));

        client.createFlow("flow:\n  id: x\n");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/manager/flows");
        assertThat(req.getHeader("Content-Type")).startsWith("text/plain");
        assertThat(req.getBody().readUtf8()).contains("id: x");
    }

    @Test @DisplayName("updateFlow PUT on /manager/flows/{id}/versions/{v}")
    void updateFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));

        client.updateFlow("x", "1.0.0", "yaml-content");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("PUT");
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/versions/1.0.0");
        assertThat(req.getBody().readUtf8()).isEqualTo("yaml-content");
    }

    @Test @DisplayName("deleteFlow DELETE on /manager/flows/{id}/versions/{v}")
    void deleteFlowOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        client.deleteFlow("x", "1.0.0");
        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("DELETE");
        assertThat(req.getPath()).isEqualTo("/manager/flows/x/versions/1.0.0");
    }
}
