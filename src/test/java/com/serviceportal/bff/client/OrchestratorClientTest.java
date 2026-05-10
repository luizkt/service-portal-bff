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

class OrchestratorClientTest {

    private MockWebServer mockWebServer;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        OrchestratorAuthService authService = mock(OrchestratorAuthService.class);
        when(authService.getToken()).thenReturn("orch-token");

        client = new OrchestratorClient(webClient, authService);
    }

    @AfterEach
    void tearDown() throws IOException { mockWebServer.shutdown(); }

    @Test @DisplayName("orchestrate posta payload no path com version+flowId e envia Authorization")
    void orchestrateOk() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"executionId\":\"abc\",\"status\":\"SUCCESS\"}"));

        Map<String, Object> resp = client.orchestrate("v2", "criar-pedido", Map.of("clienteId", "ABC123"));
        assertThat(resp).containsEntry("executionId", "abc");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/orchestrate/v2/criar-pedido");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer orch-token");
        assertThat(req.getBody().readUtf8()).contains("ABC123");
    }
}
