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

import com.serviceportal.bff.client.BackendTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Traduz falhas dos backends (Manager/Orquestrador) em respostas HTTP coerentes. Cobre o que os
 * controllers não tratam (eles ainda capturam NotFound, Conflict e BadRequest). O corpo é sempre
 * {@code {status, error, message}}: sem stack trace e sem o corpo devolvido pelo backend, que pode
 * vazar detalhes internos (o detalhe vai só para o log).
 *
 * <ul>
 *   <li>backend 4xx: mesmo status;
 *   <li>backend 5xx (ou outro status inesperado): 502;
 *   <li>timeout (conexão/resposta/prazo total): 504;
 *   <li>falha de conexão: 503.
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class BackendExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<Map<String, Object>> handleResponse(WebClientResponseException ex) {
        HttpStatusCode backend = ex.getStatusCode();
        if (backend.is4xxClientError()) {
            log.warn(
                    "Backend respondeu {} em {} {}",
                    backend.value(),
                    ex.getRequest() != null ? ex.getRequest().getMethod() : "?",
                    ex.getRequest() != null ? ex.getRequest().getURI().getPath() : "?");
            HttpStatus status = HttpStatus.resolve(backend.value());
            return body(
                    backend,
                    status != null ? status.getReasonPhrase() : "Client Error",
                    "O serviço de backend rejeitou a requisição");
        }
        log.error(
                "Backend respondeu {} em {} {}",
                backend.value(),
                ex.getRequest() != null ? ex.getRequest().getMethod() : "?",
                ex.getRequest() != null ? ex.getRequest().getURI().getPath() : "?");
        return body(HttpStatus.BAD_GATEWAY, "Bad Gateway", "O serviço de backend falhou");
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<Map<String, Object>> handleRequest(WebClientRequestException ex) {
        if (isTimeout(ex)) {
            return timeout(ex);
        }
        log.error("Falha de conexão com o backend: {}", ex.getMessage());
        return body(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "O serviço de backend está indisponível");
    }

    @ExceptionHandler({
        BackendTimeoutException.class,
        TimeoutException.class,
        io.netty.handler.timeout.TimeoutException.class
    })
    public ResponseEntity<Map<String, Object>> handleTimeout(Exception ex) {
        return timeout(ex);
    }

    private ResponseEntity<Map<String, Object>> timeout(Exception ex) {
        log.error("Timeout na chamada ao backend: {}", ex.getMessage());
        return body(
                HttpStatus.GATEWAY_TIMEOUT,
                "Gateway Timeout",
                "O serviço de backend não respondeu a tempo");
    }

    private static boolean isTimeout(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException
                    || t instanceof io.netty.handler.timeout.TimeoutException
                    || t instanceof io.netty.channel.ConnectTimeoutException
                    || t instanceof BackendTimeoutException
                    || t instanceof java.net.SocketTimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static ResponseEntity<Map<String, Object>> body(
            HttpStatusCode status, String error, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status.value());
        map.put("error", error);
        map.put("message", message);
        return ResponseEntity.status(status).body(map);
    }
}
