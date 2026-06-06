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

import com.serviceportal.bff.client.ManagerClient;
import com.serviceportal.bff.client.OrchestratorClient;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Proxy do BFF: - CRUD de fluxos → service-portal-manager (`ManagerClient`) - Execução do fluxo →
 * generic-orchestrator (`OrchestratorClient`)
 *
 * <p>Acesso restrito ao módulo de workflows: requer grupo ADMIN ou WORKFLOWS no JWT.
 *
 * <p>Endpoints REST-shape: - GET /bff/flows?page=&size=&sort=&status= - GET
 * /bff/flows/{flowId}/versions/{version} - GET /bff/flows/{flowId}/versions/{version}/yaml - POST
 * /bff/flows - PUT /bff/flows/{flowId}/versions/{version} - DELETE
 * /bff/flows/{flowId}/versions/{version} - POST /bff/flows/{flowId}/versions/{version}/executions
 * (sequencial — orquestrador v1) - POST /bff/flows/{flowId}/versions/{version}/executions/v2
 * (paralelo — orquestrador v2)
 */
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
public class FlowProxyController {

    private final ManagerClient managerClient;
    private final OrchestratorClient orchestratorClient;

    @GetMapping("/flows")
    public ResponseEntity<Object> listFlows(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listFlows(page, size, sort, status));
    }

    @GetMapping("/flows/{flowId}/versions/{version}")
    public ResponseEntity<Map<String, Object>> getFlow(
            @PathVariable String flowId, @PathVariable String version) {
        try {
            return ResponseEntity.ok(managerClient.getFlow(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(
            value = "/flows/{flowId}/versions/{version}/yaml",
            produces = {"application/x-yaml", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getFlowYaml(
            @PathVariable String flowId, @PathVariable String version) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(managerClient.getFlowYaml(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/flows/{flowId}/versions/{version}/examples")
    public ResponseEntity<Map<String, Object>> getFlowExample(
            @PathVariable String flowId, @PathVariable String version) {
        try {
            return ResponseEntity.ok(managerClient.getFlowExample(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(
            value = "/flows",
            consumes = {
                MediaType.TEXT_PLAIN_VALUE,
                "application/x-yaml",
                "text/yaml",
                MediaType.APPLICATION_JSON_VALUE
            })
    public ResponseEntity<Map<String, Object>> createFlow(@RequestBody String yaml) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.createFlow(yaml));
        } catch (WebClientResponseException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (WebClientResponseException.BadRequest e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping(
            value = "/flows/{flowId}/versions/{version}",
            consumes = {
                MediaType.TEXT_PLAIN_VALUE,
                "application/x-yaml",
                "text/yaml",
                MediaType.APPLICATION_JSON_VALUE
            })
    public ResponseEntity<Map<String, Object>> updateFlow(
            @PathVariable String flowId, @PathVariable String version, @RequestBody String yaml) {
        try {
            return ResponseEntity.ok(managerClient.updateFlow(flowId, version, yaml));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/flows/{flowId}/versions/{version}")
    public ResponseEntity<Void> deleteFlow(
            @PathVariable String flowId, @PathVariable String version) {
        try {
            managerClient.deleteFlow(flowId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Execução sequencial — delega para orquestrador v1. */
    @PostMapping("/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<Map<String, Object>> executeFlow(
            @PathVariable String flowId,
            @PathVariable String version,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(orchestratorClient.execute(flowId, version, payload));
    }

    /** Execução paralela — delega para orquestrador v2 (Java Virtual Threads). */
    @PostMapping("/flows/{flowId}/versions/{version}/executions/v2")
    public ResponseEntity<Map<String, Object>> executeFlowV2(
            @PathVariable String flowId,
            @PathVariable String version,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(orchestratorClient.executeV2(flowId, version, payload));
    }
}
