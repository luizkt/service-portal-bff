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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "Flows")
@RestController
@RequestMapping("/bff")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
public class FlowProxyController {

    private final ManagerClient managerClient;
    private final OrchestratorClient orchestratorClient;

    @Operation(
            summary = "Listar flows (paginado)",
            description =
                    "Lista flows do Manager. Use status=active para obter todos os ativos sem paginação.")
    @ApiResponse(
            responseCode = "200",
            description = "Página de flows ou lista de ativos",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"content\":[{\"flowId\":\"create-order-v1\",\"version\":\"1.0.0\",\"description\":\"Fluxo de criação de pedido\",\"active\":true}],\"totalElements\":1,\"totalPages\":1,\"size\":20,\"number\":0}")))
    @GetMapping("/flows")
    public ResponseEntity<Object> listFlows(
            @Parameter(description = "Número da página (0-based)", example = "0")
                    @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "Itens por página", example = "20")
                    @RequestParam(defaultValue = "20")
                    int size,
            @RequestParam(required = false) String sort,
            @Parameter(
                            description = "Use 'active' para listar todos os ativos sem paginação",
                            example = "active")
                    @RequestParam(required = false)
                    String status) {
        return ResponseEntity.ok(managerClient.listFlows(page, size, sort, status));
    }

    @Operation(summary = "Buscar flow por versão")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Flow encontrado",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"flowId\":\"create-order-v1\",\"version\":\"1.0.0\",\"description\":\"Fluxo de criação de pedido\",\"active\":true}"))),
        @ApiResponse(responseCode = "404", description = "Flow não encontrado")
    })
    @GetMapping("/flows/{flowId}/versions/{version}")
    public ResponseEntity<Map<String, Object>> getFlow(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer", example = "1.0.0") @PathVariable
                    String version) {
        try {
            return ResponseEntity.ok(managerClient.getFlow(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Obter YAML cru do flow",
            description = "Retorna o YAML de definição do flow como text/plain.")
    @ApiResponse(responseCode = "200", description = "YAML do flow")
    @ApiResponse(responseCode = "404", description = "Flow não encontrado")
    @GetMapping(
            value = "/flows/{flowId}/versions/{version}/yaml",
            produces = {"application/x-yaml", MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<String> getFlowYaml(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer", example = "1.0.0") @PathVariable
                    String version) {
        try {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(managerClient.getFlowYaml(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Exemplo de payload para execução",
            description =
                    "Gera JSON de exemplo a partir dos campos do contrato. Usado para pré-popular o textarea de execução no frontend.")
    @ApiResponse(
            responseCode = "200",
            description = "Exemplo gerado",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"example\":{\"clientId\":\"exemplo\",\"amount\":0},\"fields\":[{\"name\":\"clientId\",\"type\":\"STRING\",\"required\":true},{\"name\":\"amount\",\"type\":\"DECIMAL\",\"required\":true}]}")))
    @GetMapping("/flows/{flowId}/versions/{version}/examples")
    public ResponseEntity<Map<String, Object>> getFlowExample(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer", example = "1.0.0") @PathVariable
                    String version) {
        try {
            return ResponseEntity.ok(managerClient.getFlowExample(flowId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Criar flow via YAML",
            description = "Envia o YAML de definição do flow. Versão inicial sempre 1.0.0.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Flow criado",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"flowId\":\"create-order-v1\",\"version\":\"1.0.0\",\"description\":\"Fluxo de criação de pedido\",\"active\":true}"))),
        @ApiResponse(responseCode = "409", description = "Flow já existe com esse flowId + version")
    })
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

    @Operation(
            summary = "Atualizar flow (gera nova versão SemVer)",
            description =
                    "Desativa a versão atual e cria nova versão via SemVer: MAJOR se contract mudou, MINOR se integrations mudou, PATCH para demais campos.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Nova versão criada",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"flowId\":\"create-order-v1\",\"version\":\"1.1.0\",\"active\":true}"))),
        @ApiResponse(responseCode = "404", description = "Flow não encontrado")
    })
    @PutMapping(
            value = "/flows/{flowId}/versions/{version}",
            consumes = {
                MediaType.TEXT_PLAIN_VALUE,
                "application/x-yaml",
                "text/yaml",
                MediaType.APPLICATION_JSON_VALUE
            })
    public ResponseEntity<Map<String, Object>> updateFlow(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer atual", example = "1.0.0") @PathVariable
                    String version,
            @RequestBody String yaml) {
        try {
            return ResponseEntity.ok(managerClient.updateFlow(flowId, version, yaml));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Desativar flow")
    @ApiResponse(responseCode = "204", description = "Flow desativado")
    @ApiResponse(responseCode = "404", description = "Flow não encontrado")
    @DeleteMapping("/flows/{flowId}/versions/{version}")
    public ResponseEntity<Void> deleteFlow(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer", example = "1.0.0") @PathVariable
                    String version) {
        try {
            managerClient.deleteFlow(flowId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
            summary = "Executar flow (v1 — sequencial)",
            description = "Executa integrações sequencialmente. Delega para o orquestrador v1.")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Execução concluída",
                content =
                        @Content(
                                examples =
                                        @ExampleObject(
                                                value =
                                                        "{\"flowId\":\"create-order-v1\",\"version\":\"1.0.0\",\"status\":\"SUCCESS\",\"result\":{\"validate-client\":{\"clientId\":\"ABC123\",\"name\":\"Cliente WireMock\",\"active\":true},\"save-order\":{\"orderId\":\"ord-uuid-here\",\"status\":\"CREATED\"}},\"validations\":{\"check-credit-limit\":{\"approved\":true,\"limit\":5000.0}}}")))
    })
    @PostMapping("/flows/{flowId}/versions/{version}/executions")
    public ResponseEntity<Map<String, Object>> executeFlow(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer", example = "1.0.0") @PathVariable
                    String version,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(orchestratorClient.execute(flowId, version, payload));
    }

    @Operation(
            summary = "Executar flow (v2 — paralelo)",
            description =
                    "Executa integrações com mesmo 'order' em paralelo via Java Virtual Threads. Delega para o orquestrador v2.")
    @ApiResponse(
            responseCode = "200",
            description = "Execução concluída",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            value =
                                                    "{\"flowId\":\"create-order-v1\",\"version\":\"1.0.0\",\"status\":\"SUCCESS\",\"result\":{\"validate-client\":{\"clientId\":\"ABC123\",\"active\":true}}}")))
    @PostMapping("/flows/{flowId}/versions/{version}/executions/v2")
    public ResponseEntity<Map<String, Object>> executeFlowV2(
            @Parameter(description = "ID do flow", example = "create-order-v1") @PathVariable
                    String flowId,
            @Parameter(description = "Versão SemVer", example = "1.0.0") @PathVariable
                    String version,
            @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(orchestratorClient.executeV2(flowId, version, payload));
    }
}
