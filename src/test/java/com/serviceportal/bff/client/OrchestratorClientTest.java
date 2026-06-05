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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class OrchestratorClientTest {

    private MockWebServer mockWebServer;
    private OrchestratorClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient =
                WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build();
        OrchestratorAuthService authService = mock(OrchestratorAuthService.class);
        when(authService.getToken()).thenReturn("orch-token");

        client = new OrchestratorClient(webClient, authService);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName(
            "execute (v1) posts to /api/v1/flows/{id}/versions/{v}/executions with Authorization")
    void executeOk() throws InterruptedException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"executionId\":\"abc\",\"status\":\"SUCCESS\"}"));

        Map<String, Object> resp =
                client.execute("create-order", "1.0.0", Map.of("clientId", "ABC123"));
        assertThat(resp).containsEntry("executionId", "abc");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/v1/flows/create-order/versions/1.0.0/executions");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer orch-token");
        assertThat(req.getBody().readUtf8()).contains("ABC123");
    }

    @Test
    @DisplayName("executeV2 posts to /api/v2/flows/{id}/versions/{v}/executions with Authorization")
    void executeV2Ok() throws InterruptedException {
        mockWebServer.enqueue(
                new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"executionId\":\"xyz\",\"status\":\"SUCCESS\"}"));

        Map<String, Object> resp =
                client.executeV2("create-order", "1.0.0", Map.of("clientId", "DEF456"));
        assertThat(resp).containsEntry("executionId", "xyz");

        RecordedRequest req = mockWebServer.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/api/v2/flows/create-order/versions/1.0.0/executions");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer orch-token");
        assertThat(req.getBody().readUtf8()).contains("DEF456");
    }
}
