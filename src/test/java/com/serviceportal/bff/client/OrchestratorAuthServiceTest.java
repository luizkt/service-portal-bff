package com.serviceportal.bff.client;

import com.serviceportal.bff.config.BffProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrchestratorAuthServiceTest {

    private MockWebServer mockWebServer;
    private OrchestratorAuthService authService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        BffProperties props = new BffProperties();
        props.setBaseUrl(mockWebServer.url("/").toString());
        props.setUsername("admin");
        props.setPassword("admin");

        WebClient webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        authService = new OrchestratorAuthService(webClient, props);
    }

    @AfterEach
    void tearDown() throws IOException { mockWebServer.shutdown(); }

    @Test @DisplayName("getToken faz login e cacheia o resultado")
    void getTokenLogin() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"token\":\"jwt-orch\"}"));

        assertThat(authService.getToken()).isEqualTo("jwt-orch");
        assertThat(authService.getToken()).isEqualTo("jwt-orch");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test @DisplayName("getToken lança quando orquestrador devolve resposta sem token")
    void semToken() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));
        assertThatThrownBy(() -> authService.getToken())
                .isInstanceOf(IllegalStateException.class);
    }
}
