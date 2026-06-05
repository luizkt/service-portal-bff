package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.ManagerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.serviceportal.bff.controller.IntegrationProxyControllerTest.badRequest;
import static com.serviceportal.bff.controller.IntegrationProxyControllerTest.conflict;
import static com.serviceportal.bff.controller.IntegrationProxyControllerTest.notFound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContractProxyControllerTest {

    private ManagerClient client;
    private ContractProxyController controller;

    @BeforeEach
    void setUp() {
        client = mock(ManagerClient.class);
        controller = new ContractProxyController(client);
    }

    @Test
    void list() {
        when(client.listContracts(0, 20, null, null)).thenReturn(Map.of("content", List.of()));
        assertThat(controller.list(0, 20, null, null).getStatusCode().value()).isEqualTo(200);
        verify(client).listContracts(0, 20, null, null);
    }

    @Test
    void listVersions() {
        when(client.listContractVersions("co", "active")).thenReturn(List.of());
        assertThat(controller.listVersions("co", "active").getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getOk() {
        when(client.getContract("co", 1)).thenReturn(Map.of("contractId", "co"));
        assertThat(controller.get("co", 1).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void getNotFound() {
        when(client.getContract("co", 9)).thenThrow(notFound());
        assertThat(controller.get("co", 9).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void createOk() {
        when(client.createContract(any())).thenReturn(Map.of("contractId", "co", "version", 1));
        assertThat(controller.create(Map.of("contractId", "co")).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void createConflict() {
        when(client.createContract(any())).thenThrow(conflict());
        assertThat(controller.create(Map.of()).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void createBadRequest() {
        when(client.createContract(any())).thenThrow(badRequest());
        assertThat(controller.create(Map.of()).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void updateOk() {
        when(client.updateContract(any(), any(Integer.class), any())).thenReturn(Map.of("version", 2));
        assertThat(controller.update("co", 1, Map.of()).getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void updateNotFound() {
        when(client.updateContract(any(), any(Integer.class), any())).thenThrow(notFound());
        assertThat(controller.update("co", 1, Map.of()).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void deleteOk() {
        assertThat(controller.delete("co", 1).getStatusCode().value()).isEqualTo(204);
        verify(client).deleteContract("co", 1);
    }

    @Test
    void deleteNotFound() {
        doThrow(notFound()).when(client).deleteContract("co", 9);
        assertThat(controller.delete("co", 9).getStatusCode().value()).isEqualTo(404);
    }
}
