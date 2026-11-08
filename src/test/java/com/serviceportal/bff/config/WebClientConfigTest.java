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
package com.serviceportal.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class WebClientConfigTest {

    @Test
    @DisplayName("beans dos dois backends são criados com os timeouts configurados")
    void beans() {
        WebClientConfig config = new WebClientConfig();
        BffProperties orch = new BffProperties();
        orch.setBaseUrl("http://localhost:1");
        ManagerProperties mgr = new ManagerProperties();
        mgr.setBaseUrl("http://localhost:2");
        HttpProperties http = new HttpProperties();

        assertThat(config.orchestratorWebClient(WebClient.builder(), orch, http)).isNotNull();
        assertThat(config.managerWebClient(WebClient.builder(), mgr, http)).isNotNull();
        assertThat(http.getConnectTimeoutMs()).isEqualTo(2000);
        assertThat(mgr.getTimeoutMs()).isEqualTo(10000);
        assertThat(orch.getTimeoutMs()).isEqualTo(30000);
    }
}
