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
 * Proxy do BFF para validations → service-portal-manager.
 *
 * <p>Acesso (espelha as regras do Manager): - Leitura (GET): ADMIN, RULES - Escrita
 * (POST/PUT/DELETE): ADMIN, RULES
 */
@io.swagger.v3.oas.annotations.tags.Tag(name = "Validations")
@RestController
@RequestMapping("/bff/validations")
@RequiredArgsConstructor
public class ValidationProxyController {

    private final ManagerClient managerClient;

    @Operation(summary = "Listar validações (paginado)")
    @ApiResponse(responseCode = "200", description = "Página de validações ou lista de ativas")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @Parameter(description = "Use 'active' para listar todas as ativas")
                    @RequestParam(required = false)
                    String status) {
        return ResponseEntity.ok(managerClient.listValidations(page, size, sort, status));
    }

    @Operation(summary = "Histórico de versões de uma validação")
    @ApiResponse(responseCode = "200", description = "Lista de versões")
    @GetMapping("/{validationId}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Object> listVersions(
            @Parameter(description = "ID da validação", example = "check-credit-limit")
                    @PathVariable
                    String validationId,
            @Parameter(description = "Filtro: active | inactive") @RequestParam(required = false)
                    String status) {
        return ResponseEntity.ok(managerClient.listValidationVersions(validationId, status));
    }

    @Operation(summary = "Buscar validação por versão")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Validação encontrada"),
        @ApiResponse(responseCode = "404", description = "Não encontrada")
    })
    @GetMapping("/{validationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Map<String, Object>> get(
            @Parameter(description = "ID da validação", example = "check-credit-limit")
                    @PathVariable
                    String validationId,
            @Parameter(description = "Número da versão", example = "1") @PathVariable int version) {
        try {
            return ResponseEntity.ok(managerClient.getValidation(validationId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Criar validação")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Criada"),
        @ApiResponse(responseCode = "409", description = "Já existe")
    })
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(managerClient.createValidation(body));
        } catch (WebClientResponseException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (WebClientResponseException.BadRequest e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Atualizar validação (nova versão)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Nova versão criada"),
        @ApiResponse(responseCode = "404", description = "Não encontrada")
    })
    @PutMapping("/{validationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Map<String, Object>> update(
            @Parameter(description = "ID da validação", example = "check-credit-limit")
                    @PathVariable
                    String validationId,
            @Parameter(description = "Número da versão atual", example = "1") @PathVariable
                    int version,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(managerClient.updateValidation(validationId, version, body));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Desativar validação")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Desativada"),
        @ApiResponse(responseCode = "404", description = "Não encontrada")
    })
    @DeleteMapping("/{validationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Void> delete(
            @Parameter(description = "ID da validação", example = "check-credit-limit")
                    @PathVariable
                    String validationId,
            @Parameter(description = "Número da versão", example = "1") @PathVariable int version) {
        try {
            managerClient.deleteValidation(validationId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }
}
