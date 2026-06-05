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
 * Proxy do BFF para integrations → service-portal-manager.
 *
 * Acesso (espelha as regras do Manager):
 *   - Leitura (GET): ADMIN, WORKFLOWS
 *   - Escrita (POST/PUT/DELETE): somente ADMIN
 */
@RestController
@RequestMapping("/bff/integrations")
@RequiredArgsConstructor
public class IntegrationProxyController {

    private final ManagerClient managerClient;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listIntegrations(page, size, sort, status));
    }

    @GetMapping("/{integrationId}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Object> listVersions(@PathVariable String integrationId,
                                               @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listIntegrationVersions(integrationId, status));
    }

    @GetMapping("/{integrationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'WORKFLOWS')")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String integrationId,
                                                   @PathVariable int version) {
        try {
            return ResponseEntity.ok(managerClient.getIntegration(integrationId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.createIntegration(body));
        } catch (WebClientResponseException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (WebClientResponseException.BadRequest e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{integrationId}/versions/{version}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String integrationId,
                                                      @PathVariable int version,
                                                      @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.updateIntegration(integrationId, version, body));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{integrationId}/versions/{version}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable String integrationId,
                                       @PathVariable int version) {
        try {
            managerClient.deleteIntegration(integrationId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }
}
