/*
 * MIT License
 *
 * Copyright (c) 2026 Luiz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.serviceportal.bff.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.serviceportal.bff.config.BffProperties;
import com.serviceportal.bff.config.ManagerProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class UnauthorizedRetryTest {

    private MockWebServer server;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static String exp(long secondsFromNow) {
        return TokenExpiryTest.jwt(
                "{\"exp\":" + (Instant.now().getEpochSecond() + secondsFromNow) + "}");
    }

    @Test
    @DisplayName("Manager: 401 invalida o token e repete UMA vez com token novo")
    void managerRetriesOnce() throws InterruptedException {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        ManagerClient client = new ManagerClient(webClient, auth);
        server.enqueue(json(200, "{\"token\":\"old\"}"));
        server.enqueue(json(401, "{}"));
        server.enqueue(json(200, "{\"token\":\"new\"}"));
        server.enqueue(json(200, "{\"flowId\":\"f\"}"));

        Map<String, Object> flow = client.getFlow("f", "1.0.0");

        assertThat(flow).containsEntry("flowId", "f");
        assertThat(server.getRequestCount()).isEqualTo(4);
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/auth/tokens");
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer old");
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/auth/tokens");
        RecordedRequest retried = server.takeRequest();
        assertThat(retried.getHeader("Authorization")).isEqualTo("Bearer new");
        assertThat(retried.getPath()).isEqualTo("/manager/flows/f/versions/1.0.0");
    }

    @Test
    @DisplayName("Manager: segundo 401 não gera novo retry e propaga o erro")
    void managerDoesNotRetryTwice() {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        ManagerClient client = new ManagerClient(webClient, auth);
        server.enqueue(json(200, "{\"token\":\"old\"}"));
        server.enqueue(json(401, "{}"));
        server.enqueue(json(200, "{\"token\":\"new\"}"));
        server.enqueue(json(401, "{}"));

        assertThatThrownBy(() -> client.getFlow("f", "1.0.0"))
                .isInstanceOf(WebClientResponseException.Unauthorized.class);
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("Manager: resposta não-401 não dispara retry")
    void managerNoRetryOnOther() {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        ManagerClient client = new ManagerClient(webClient, auth);
        server.enqueue(json(200, "{\"token\":\"old\"}"));
        server.enqueue(json(404, "{}"));

        assertThatThrownBy(() -> client.getFlow("f", "1.0.0"))
                .isInstanceOf(WebClientResponseException.NotFound.class);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Orchestrator: 401 invalida o token e repete UMA vez com token novo")
    void orchestratorRetriesOnce() throws InterruptedException {
        OrchestratorAuthService auth = new OrchestratorAuthService(webClient, new BffProperties());
        OrchestratorClient client = new OrchestratorClient(webClient, auth);
        server.enqueue(json(200, "{\"token\":\"old\"}"));
        server.enqueue(json(401, "{}"));
        server.enqueue(json(200, "{\"token\":\"new\"}"));
        server.enqueue(json(200, "{\"ok\":true}"));

        assertThat(client.execute("f", "1", Map.of("a", 1))).containsEntry("ok", true);

        server.takeRequest();
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer old");
        server.takeRequest();
        RecordedRequest retried = server.takeRequest();
        assertThat(retried.getHeader("Authorization")).isEqualTo("Bearer new");
        assertThat(retried.getBody().readUtf8()).contains("\"a\":1");
    }

    @Test
    @DisplayName("invalidate só descarta o token se for o que falhou")
    void invalidateOnlyFailedToken() {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        server.enqueue(json(200, "{\"token\":\"t1\"}"));
        server.enqueue(json(200, "{\"token\":\"t2\"}"));

        assertThat(auth.getToken()).isEqualTo("t1");
        auth.invalidate("outro");
        auth.invalidate(null);
        assertThat(auth.getToken()).isEqualTo("t1");
        auth.invalidate("t1");
        assertThat(auth.getToken()).isEqualTo("t2");
    }

    @Test
    @DisplayName("Manager: token com exp perto demais (margem de 60 s) é renovado a cada chamada")
    void managerExpiryInsideMargin() {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        String shortLived = exp(30);
        server.enqueue(json(200, "{\"token\":\"" + shortLived + "\"}"));
        server.enqueue(json(200, "{\"token\":\"seg\"}"));

        assertThat(auth.getToken()).isEqualTo(shortLived);
        assertThat(auth.getToken()).isEqualTo("seg");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Manager: expiresIn e exp longo mantêm o cache")
    void managerLongExpiryCached() {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        server.enqueue(json(200, "{\"token\":\"a\",\"expiresIn\":7200}"));

        assertThat(auth.getToken()).isEqualTo("a");
        assertThat(auth.getToken()).isEqualTo("a");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Manager: expiresIn curto força renovação")
    void managerShortExpiresIn() {
        ManagerAuthService auth = new ManagerAuthService(webClient, new ManagerProperties());
        server.enqueue(json(200, "{\"token\":\"a\",\"expires_in\":30}"));
        server.enqueue(json(200, "{\"token\":\"b\"}"));

        assertThat(auth.getToken()).isEqualTo("a");
        assertThat(auth.getToken()).isEqualTo("b");
    }

    @Test
    @DisplayName("Orchestrator: exp do JWT governa o cache")
    void orchestratorExpiry() {
        OrchestratorAuthService auth = new OrchestratorAuthService(webClient, new BffProperties());
        String shortLived = exp(10);
        String longLived = exp(7200);
        server.enqueue(json(200, "{\"token\":\"" + shortLived + "\"}"));
        server.enqueue(json(200, "{\"token\":\"" + longLived + "\"}"));

        assertThat(auth.getToken()).isEqualTo(shortLived);
        assertThat(auth.getToken()).isEqualTo(longLived);
        assertThat(auth.getToken()).isEqualTo(longLived);
        assertThat(server.getRequestCount()).isEqualTo(2);
        auth.invalidate(longLived);
        server.enqueue(json(200, "{\"token\":\"z\"}"));
        assertThat(auth.getToken()).isEqualTo("z");
    }
}
