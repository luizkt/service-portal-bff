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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Proxy do BFF para contracts → service-portal-manager.
 *
 * <p>Acesso (espelha as regras do Manager): - Leitura (GET): ADMIN, WORKFLOWS, RULES - Escrita
 * (POST/PUT/DELETE): ADMIN, WORKFLOWS
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "Contracts")
@RestController
@RequestMapping("/bff/contracts")
@RequiredArgsConstructor
public class ContractProxyController {

    private final ManagerClient managerClient;

    @Operation(summary = "Listar contratos (paginado)")
    @ApiResponse(responseCode = "200", description = "Página de contratos ou lista de ativos")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS', 'RULES')")
    public ResponseEntity<Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @Parameter(description = "Use 'active' para listar todos os ativos")
                    @RequestParam(required = false)
                    String status) {
        return ResponseEntity.ok(managerClient.listContracts(page, size, sort, status));
    }

    @Operation(summary = "Histórico de versões de um contrato")
    @ApiResponse(responseCode = "200", description = "Lista de versões")
    @GetMapping("/{contractId}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS', 'RULES')")
    public ResponseEntity<Object> listVersions(
            @Parameter(description = "ID do contrato", example = "create-order") @PathVariable
                    String contractId,
            @Parameter(description = "Filtro: active | inactive") @RequestParam(required = false)
                    String status) {
        return ResponseEntity.ok(managerClient.listContractVersions(contractId, status));
    }

    @Operation(summary = "Buscar contrato por versão")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contrato encontrado"),
        @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    @GetMapping("/{contractId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS', 'RULES')")
    public ResponseEntity<Map<String, Object>> get(
            @Parameter(description = "ID do contrato", example = "create-order") @PathVariable
                    String contractId,
            @Parameter(description = "Número da versão", example = "1") @PathVariable int version) {
        try {
            return ResponseEntity.ok(managerClient.getContract(contractId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Criar contrato")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Criado"),
        @ApiResponse(responseCode = "409", description = "Já existe")
    })
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(managerClient.createContract(body));
        } catch (WebClientResponseException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (WebClientResponseException.BadRequest e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Atualizar contrato (nova versão)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Nova versão criada"),
        @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    @PutMapping("/{contractId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Map<String, Object>> update(
            @Parameter(description = "ID do contrato", example = "create-order") @PathVariable
                    String contractId,
            @Parameter(description = "Número da versão atual", example = "1") @PathVariable
                    int version,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(managerClient.updateContract(contractId, version, body));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Desativar contrato")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Desativado"),
        @ApiResponse(responseCode = "404", description = "Não encontrado")
    })
    @DeleteMapping("/{contractId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID do contrato", example = "create-order") @PathVariable
                    String contractId,
            @Parameter(description = "Número da versão", example = "1") @PathVariable int version) {
        try {
            managerClient.deleteContract(contractId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }
}
