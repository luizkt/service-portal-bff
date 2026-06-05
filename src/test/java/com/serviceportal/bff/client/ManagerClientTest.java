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
import java.util.Map;

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
        assertThat(resp).isNotNull();

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

    // ===================================================================
    // Recursos modulares: integrations / contracts / validations
    // Ciclo CRUD completo por recurso, validando path/método/headers.
    // ===================================================================

    private void enqueueJson(String body, int code) {
        mockWebServer.enqueue(new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/json").setBody(body));
    }

    @Test @DisplayName("integrations: list/get/versions/create/update/delete batem nos paths corretos")
    void integrationsCrud() throws InterruptedException {
        enqueueJson("{\"content\":[]}", 200);
        client.listIntegrations(0, 20, "integrationId,asc", "active");
        RecordedRequest r = mockWebServer.takeRequest();
        assertThat(r.getPath()).contains("/manager/integrations").contains("page=0").contains("size=20")
                .contains("sort=integrationId,asc").contains("status=active");
        assertThat(r.getHeader("Authorization")).isEqualTo("Bearer test-token");

        enqueueJson("{\"integrationId\":\"vc\",\"version\":1}", 200);
        client.getIntegration("vc", 1);
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/integrations/vc/versions/1");

        enqueueJson("[]", 200);
        client.listIntegrationVersions("vc", "inactive");
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/integrations/vc/versions?status=inactive");

        enqueueJson("{\"integrationId\":\"vc\",\"version\":1}", 201);
        client.createIntegration(Map.of("integrationId", "vc"));
        RecordedRequest c = mockWebServer.takeRequest();
        assertThat(c.getMethod()).isEqualTo("POST");
        assertThat(c.getPath()).isEqualTo("/manager/integrations");
        assertThat(c.getHeader("Content-Type")).contains("application/json");

        enqueueJson("{\"version\":2}", 201);
        client.updateIntegration("vc", 1, Map.of("url", "http://x"));
        RecordedRequest u = mockWebServer.takeRequest();
        assertThat(u.getMethod()).isEqualTo("PUT");
        assertThat(u.getPath()).isEqualTo("/manager/integrations/vc/versions/1");

        enqueueJson("", 204);
        client.deleteIntegration("vc", 1);
        RecordedRequest d = mockWebServer.takeRequest();
        assertThat(d.getMethod()).isEqualTo("DELETE");
        assertThat(d.getPath()).isEqualTo("/manager/integrations/vc/versions/1");
    }

    @Test @DisplayName("contracts: paths corretos (list sem sort/status, versions sem status)")
    void contractsCrud() throws InterruptedException {
        enqueueJson("{\"content\":[]}", 200);
        client.listContracts(1, 50, null, null);
        assertThat(mockWebServer.takeRequest().getPath())
                .contains("/manager/contracts").contains("page=1").contains("size=50").doesNotContain("sort=").doesNotContain("status=");

        enqueueJson("{\"contractId\":\"co\"}", 200);
        client.getContract("co", 2);
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/contracts/co/versions/2");

        enqueueJson("[]", 200);
        client.listContractVersions("co", null);
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/contracts/co/versions");

        enqueueJson("{\"contractId\":\"co\"}", 201);
        client.createContract(Map.of("contractId", "co"));
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/contracts");

        enqueueJson("{\"version\":2}", 201);
        client.updateContract("co", 1, Map.of());
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/contracts/co/versions/1");

        enqueueJson("", 204);
        client.deleteContract("co", 1);
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/contracts/co/versions/1");
    }

    @Test @DisplayName("validations: paths corretos")
    void validationsCrud() throws InterruptedException {
        enqueueJson("{\"content\":[]}", 200);
        client.listValidations(0, 20, null, null);
        assertThat(mockWebServer.takeRequest().getPath()).contains("/manager/validations");

        enqueueJson("{\"validationId\":\"vl\"}", 200);
        client.getValidation("vl", 1);
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/validations/vl/versions/1");

        enqueueJson("[]", 200);
        client.listValidationVersions("vl", "active");
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/validations/vl/versions?status=active");

        enqueueJson("{\"validationId\":\"vl\"}", 201);
        client.createValidation(Map.of("validationId", "vl"));
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/validations");

        enqueueJson("{\"version\":2}", 201);
        client.updateValidation("vl", 1, Map.of());
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/validations/vl/versions/1");

        enqueueJson("", 204);
        client.deleteValidation("vl", 1);
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/manager/validations/vl/versions/1");
    }
}
