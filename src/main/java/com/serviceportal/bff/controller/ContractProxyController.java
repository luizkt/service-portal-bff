package com.serviceportal.bff.controller;

import com.serviceportal.bff.client.ManagerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

/**
 * Proxy do BFF para contracts → service-portal-manager.
 *
 * Acesso (espelha as regras do Manager):
 *   - Leitura (GET): ADMIN, WORKFLOWS, RULES
 *   - Escrita (POST/PUT/DELETE): ADMIN, WORKFLOWS
 */
@RestController
@RequestMapping("/bff/contracts")
@RequiredArgsConstructor
public class ContractProxyController {

    private final ManagerClient managerClient;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS', 'RULES')")
    public ResponseEntity<Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listContracts(page, size, sort, status));
    }

    @GetMapping("/{contractId}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS', 'RULES')")
    public ResponseEntity<Object> listVersions(@PathVariable String contractId,
                                               @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listContractVersions(contractId, status));
    }

    @GetMapping("/{contractId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS', 'RULES')")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String contractId,
                                                   @PathVariable int version) {
        try {
            return ResponseEntity.ok(managerClient.getContract(contractId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.createContract(body));
        } catch (WebClientResponseException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (WebClientResponseException.BadRequest e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{contractId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String contractId,
                                                      @PathVariable int version,
                                                      @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.updateContract(contractId, version, body));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{contractId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Void> delete(@PathVariable String contractId,
                                       @PathVariable int version) {
        try {
            managerClient.deleteContract(contractId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }
}
