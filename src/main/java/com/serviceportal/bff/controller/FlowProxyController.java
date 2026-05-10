package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.OrchestratorClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
public class FlowProxyController {

    private final OrchestratorClient client;

    @GetMapping("/flows")
    public ResponseEntity<List<Map<String, Object>>> listFlows() {
        return ResponseEntity.ok(client.listFlows());
    }

    @GetMapping("/flows/{flowId}")
    public ResponseEntity<Map<String, Object>> getFlow(@PathVariable String flowId) {
        try {
            return ResponseEntity.ok(client.getFlow(flowId));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/flows",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> createFlow(@RequestBody String yaml) {
        return ResponseEntity.status(HttpStatus.CREATED).body(client.createFlow(yaml));
    }

    @PutMapping(value = "/flows/{flowId}",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/x-yaml", "text/yaml", MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Map<String, Object>> updateFlow(@PathVariable String flowId,
                                                          @RequestBody String yaml) {
        try {
            return ResponseEntity.ok(client.updateFlow(flowId, yaml));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/flows/{flowId}")
    public ResponseEntity<Void> deleteFlow(@PathVariable String flowId) {
        try {
            client.deleteFlow(flowId);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/orchestrate/{version}/{flowId}")
    public ResponseEntity<Map<String, Object>> orchestrate(@PathVariable String version,
                                                           @PathVariable String flowId,
                                                           @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(client.orchestrate(version, flowId, payload));
    }
}
