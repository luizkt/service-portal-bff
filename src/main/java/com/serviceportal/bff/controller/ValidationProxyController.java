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
 * Proxy do BFF para validations → service-portal-manager.
 *
 * Acesso (espelha as regras do Manager):
 *   - Leitura (GET): ADMIN, RULES
 *   - Escrita (POST/PUT/DELETE): ADMIN, RULES
 */
@RestController
@RequestMapping("/bff/validations")
@RequiredArgsConstructor
public class ValidationProxyController {

    private final ManagerClient managerClient;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Object> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listValidations(page, size, sort, status));
    }

    @GetMapping("/{validationId}/versions")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Object> listVersions(@PathVariable String validationId,
                                               @RequestParam(required = false) String status) {
        return ResponseEntity.ok(managerClient.listValidationVersions(validationId, status));
    }

    @GetMapping("/{validationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String validationId,
                                                   @PathVariable int version) {
        try {
            return ResponseEntity.ok(managerClient.getValidation(validationId, version));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.createValidation(body));
        } catch (WebClientResponseException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (WebClientResponseException.BadRequest e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/{validationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String validationId,
                                                      @PathVariable int version,
                                                      @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(managerClient.updateValidation(validationId, version, body));
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{validationId}/versions/{version}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'RULES')")
    public ResponseEntity<Void> delete(@PathVariable String validationId,
                                       @PathVariable int version) {
        try {
            managerClient.deleteValidation(validationId, version);
            return ResponseEntity.noContent().build();
        } catch (WebClientResponseException.NotFound e) {
            return ResponseEntity.notFound().build();
        }
    }
}
