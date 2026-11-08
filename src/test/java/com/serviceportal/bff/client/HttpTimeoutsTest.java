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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.serviceportal.bff.config.BffProperties;
import com.serviceportal.bff.config.ManagerProperties;
import com.serviceportal.bff.config.WebClientConfig;
import io.netty.handler.timeout.ReadTimeoutException;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

class HttpTimeoutsTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private MockResponse slow() {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{}")
                .setBodyDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                .setHeadersDelay(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("ManagerClient falha rápido com ReadTimeout quando o backend demora")
    void managerResponseTimeout() {
        server.enqueue(slow());
        WebClient wc =
                WebClientConfig.build(WebClient.builder(), server.url("/").toString(), 500, 300);
        ManagerAuthService auth = mock(ManagerAuthService.class);
        when(auth.getToken()).thenReturn("t");
        ManagerProperties props = new ManagerProperties();
        props.setTimeoutMs(300);
        ManagerClient client = new ManagerClient(wc, auth, props);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.getFlow("f", "1.0.0"))
                .isInstanceOf(WebClientRequestException.class)
                .hasRootCauseInstanceOf(ReadTimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("OrchestratorClient falha rápido com ReadTimeout quando o backend demora")
    void orchestratorResponseTimeout() {
        server.enqueue(slow());
        WebClient wc =
                WebClientConfig.build(WebClient.builder(), server.url("/").toString(), 500, 300);
        OrchestratorAuthService auth = mock(OrchestratorAuthService.class);
        when(auth.getToken()).thenReturn("t");
        BffProperties props = new BffProperties();
        props.setTimeoutMs(300);
        OrchestratorClient client = new OrchestratorClient(wc, auth, props);

        assertThatThrownBy(() -> client.execute("f", "1", Map.of()))
                .isInstanceOf(WebClientRequestException.class)
                .hasRootCauseInstanceOf(ReadTimeoutException.class);
    }

    @Test
    @DisplayName("block() é limitado pelo prazo total mesmo sem timeout no Netty")
    void blockDeadline() {
        server.enqueue(slow());
        WebClient wc = WebClient.builder().baseUrl(server.url("/").toString()).build();
        ManagerAuthService auth = mock(ManagerAuthService.class);
        when(auth.getToken()).thenReturn("t");
        ManagerProperties props = new ManagerProperties();
        props.setTimeoutMs(100);
        ManagerClient client = new ManagerClient(wc, auth, props);

        assertThatThrownBy(() -> client.getFlow("f", "1.0.0"))
                .isInstanceOf(BackendTimeoutException.class)
                .hasMessageContaining("2100 ms");
    }

    @Test
    @DisplayName("login do Manager também respeita o prazo")
    void authDeadline() {
        server.enqueue(slow());
        WebClient wc =
                WebClientConfig.build(WebClient.builder(), server.url("/").toString(), 500, 300);
        ManagerProperties props = new ManagerProperties();
        props.setTimeoutMs(300);
        ManagerAuthService auth = new ManagerAuthService(wc, props);
        assertThatThrownBy(auth::getToken).isInstanceOf(WebClientRequestException.class);
    }
}
