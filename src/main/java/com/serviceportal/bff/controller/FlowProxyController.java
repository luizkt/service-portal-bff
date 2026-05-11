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
 *
 * Endpoints REST-shape:
 *   - GET    /bff/flows?page=&size=&sort=&status=
 *   - GET    /bff/flows/{flowId}/versions/{version}
 *   - GET    /bff/flows/{flowId}/versions/{version}/yaml
 *   - POST   /bff/flows
 *   - PUT    /bff/flows/{flowId}/versions/{version}
 *   - DELETE /bff/flows/{flowId}/versions/{version}
 *   - POST   /bff/flows/{flowId}/versions/{version}/executions  (substitui /orchestrate/{v}/{id})
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
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        // Repasse simples — Manager interpreta `status=active`. Sort/page/size
        // continuam funcionando para listagem geral.
        return ResponseEntity.ok(managerClient.listFlows(page, size, sort, status));
    }

    @GetMapping("/flows/{flowId}/versions/{version}")
    public ResponseEntity<Map<String, Object>> getFlow(@PathVariable String flowId,
                                                       @PathVariable String version) {
        try {
            return ResponseEntity.ok(managerClient.getFlow(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/flows/{flowId}/versions/{version}/yaml",
            produces = {"application/x-yaml", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getFlowYaml(@PathVariable String flowId,
                                              @PathVariable String version) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(managerClient.getFlowYaml(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/flows",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> createFlow(@RequestBody String yaml) {
        return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.createFlow(yaml));
    }

    @PutMapping(value = "/flows/{flowId}/versions/{version}",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> updateFlow(@PathVariable String flowId,
                                                          @PathVariable String version,
                                                          @RequestBody String yaml) {
        try {
            return ResponseEntity.ok(managerClient.updateFlow(flowId, version, yaml));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/flows/{flowId}/versions/{version}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String flowId,
                                           @PathVariable String version) {
        try {
            managerClient.deleteFlow(flowId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Cria uma execução do fluxo {@code flowId} versão {@code version} —
     * substitui o antigo verb-based {@code POST /bff/orchestrate/{v}/{id}}.
     */
    @PostMapping("/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<Map<String, Object>> executeFlow(@PathVariable String flowId,
                                                           @PathVariable String version,
                                                           @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(orchestratorClient.execute(flowId, version, payload));
    }
}
