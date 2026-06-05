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
package com.serviceportal.bff.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serviceportal.bff.client.ManagerClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class IntegrationProxyControllerTest {

    private ManagerClient client;
    private IntegrationProxyController controller;

    @BeforeEach
    void setUp() {
        client = mock(ManagerClient.class);
        controller = new IntegrationProxyController(client);
    }

    @Test
    void list() {
        when(client.listIntegrations(0, 20, "x", "active"))
                .thenReturn(Map.of("content", List.of()));
        assertThat(controller.list(0, 20, "x", "active").getStatusCode().value()).isEqualTo(200);
        verify(client).listIntegrations(0, 20, "x", "active");
    }

    @Test
    void listVersions() {
        when(client.listIntegrationVersions("vc", null)).thenReturn(List.of());
        assertThat(controller.listVersions("vc", null).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getOk() {
        when(client.getIntegration("vc", 1)).thenReturn(Map.of("integrationId", "vc"));
        assertThat(controller.get("vc", 1).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getNotFound() {
        when(client.getIntegration("vc", 9)).thenThrow(notFound());
        assertThat(controller.get("vc", 9).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void createOk() {
        when(client.createIntegration(any()))
                .thenReturn(Map.of("integrationId", "vc", "version", 1));
        assertThat(controller.create(Map.of("integrationId", "vc")).getStatusCode().value())
                .isEqualTo(201);
    }

    @Test
    void createConflict() {
        when(client.createIntegration(any())).thenThrow(conflict());
        assertThat(controller.create(Map.of()).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void createBadRequest() {
        when(client.createIntegration(any())).thenThrow(badRequest());
        assertThat(controller.create(Map.of()).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateOk() {
        when(client.updateIntegration(any(), any(Integer.class), any()))
                .thenReturn(Map.of("version", 2));
        assertThat(controller.update("vc", 1, Map.of()).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateNotFound() {
        when(client.updateIntegration(any(), any(Integer.class), any())).thenThrow(notFound());
        assertThat(controller.update("vc", 1, Map.of()).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteOk() {
        assertThat(controller.delete("vc", 1).getStatusCode().value()).isEqualTo(204);
        verify(client).deleteIntegration("vc", 1);
    }

    @Test
    void deleteNotFound() {
        doThrow(notFound()).when(client).deleteIntegration("vc", 9);
        assertThat(controller.delete("vc", 9).getStatusCode().value()).isEqualTo(404);
    }

    static WebClientResponseException.NotFound notFound() {
        return (WebClientResponseException.NotFound)
                WebClientResponseException.create(404, "Not Found", null, null, null);
    }

    static WebClientResponseException.Conflict conflict() {
        return (WebClientResponseException.Conflict)
                WebClientResponseException.create(409, "Conflict", null, null, null);
    }

    static WebClientResponseException.BadRequest badRequest() {
        return (WebClientResponseException.BadRequest)
                WebClientResponseException.create(400, "Bad Request", null, null, null);
    }
}
