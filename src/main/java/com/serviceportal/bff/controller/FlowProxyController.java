package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Proxy do BFF:
 *   - CRUD de fluxos → service-portal-manager (`ManagerClient`)
 *   - Execução do fluxo → generic-orchestrator (`OrchestratorClient`)
 */
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
public class FlowProxyController {

    private final ManagerClient managerClient;
    private final OrchestratorClient orchestratorClient;

    @GetMapping("/flows")
    public ResponseEntity<Map<String, Object>> listFlows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(managerClient.listFlows(page, size, sort));
    }

    @GetMapping("/flows/{flowId}/{versao}")
    public ResponseEntity<Map<String, Object>> getFlow(@PathVariable String flowId,
                                                       @PathVariable String versao) {
        try {
            return ResponseEntity.ok(managerClient.getFlow(flowId, versao));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/flows/{flowId}/{versao}/yaml",
            produces = {"application/x-yaml", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getFlowYaml(@PathVariable String flowId,
                                              @PathVariable String versao) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(managerClient.getFlowYaml(flowId, versao));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/flows",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> createFlow(@RequestBody String yaml) {
        return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.createFlow(yaml));
    }

    @PutMapping(value = "/flows/{flowId}/{versao}",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> updateFlow(@PathVariable String flowId,
                                                          @PathVariable String versao,
                                                          @RequestBody String yaml) {
        try {
            return ResponseEntity.ok(managerClient.updateFlow(flowId, versao, yaml));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/flows/{flowId}/{versao}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String flowId,
                                           @PathVariable String versao) {
        try {
            managerClient.deleteFlow(flowId, versao);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/orchestrate/{version}/{flowId}")
    public ResponseEntity<Map<String, Object>> orchestrate(@PathVariable String version,
                                                           @PathVariable String flowId,
                                                           @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(orchestratorClient.orchestrate(version, flowId, payload));
    }
}
