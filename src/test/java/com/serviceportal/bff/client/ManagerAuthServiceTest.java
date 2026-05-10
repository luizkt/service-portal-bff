package com.serviceportal.bff.client;

import com.serviceportal.bff.config.ManagerProperties;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManagerAuthServiceTest {

    private MockWebServer mockWebServer;
    private ManagerAuthService authService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        ManagerProperties props = new ManagerProperties();
        props.setBaseUrl(mockWebServer.url("/").toString());
        props.setUsername("admin");
        props.setPassword("admin");

        WebClient webClient = WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        authService = new ManagerAuthService(webClient, props);
    }

    @AfterEach
    void tearDown() throws IOException { mockWebServer.shutdown(); }

    @Test @DisplayName("getToken faz login e devolve token")
    void getTokenLogin() throws InterruptedException {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"token\":\"jwt-1\"}"));

        assertThat(authService.getToken()).isEqualTo("jwt-1");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getPath()).isEqualTo("/api/auth/login");
        assertThat(req.getBody().readUtf8()).contains("admin");
    }

    @Test @DisplayName("getToken usa cache nas chamadas subsequentes")
    void getTokenCacheado() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"token\":\"cached\"}"));

        assertThat(authService.getToken()).isEqualTo("cached");
        assertThat(authService.getToken()).isEqualTo("cached");
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test @DisplayName("getToken lança quando Manager devolve resposta sem token")
    void semToken() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{}"));
        assertThatThrownBy(() -> authService.getToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sem token");
    }
}
