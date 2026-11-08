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
package com.serviceportal.bff.client;

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Se o backend responde 401 a uma chamada com token de serviço, invalida o token em cache e repete
 * a chamada UMA vez com um token novo. Cobre token invalidado antes da validade (restart do
 * backend, rotação de chave).
 */
final class UnauthorizedRetryFilter {

    private UnauthorizedRetryFilter() {}

    static ExchangeFilterFunction create(
            Supplier<String> tokenSupplier, Consumer<String> invalidator) {
        return (request, next) ->
                next.exchange(request)
                        .flatMap(
                                response -> {
                                    if (response.statusCode().value()
                                            != HttpStatus.UNAUTHORIZED.value()) {
                                        return Mono.just(response);
                                    }
                                    String used =
                                            request.headers().getFirst(HttpHeaders.AUTHORIZATION);
                                    // getToken() bloqueia: sai do event loop do Netty
                                    return response.releaseBody()
                                            .then(
                                                    Mono.fromCallable(
                                                                    () -> {
                                                                        if (used != null
                                                                                && used.startsWith(
                                                                                        "Bearer ")) {
                                                                            invalidator.accept(
                                                                                    used.substring(
                                                                                            7));
                                                                        }
                                                                        return tokenSupplier.get();
                                                                    })
                                                            .subscribeOn(
                                                                    Schedulers.boundedElastic()))
                                            .flatMap(
                                                    fresh ->
                                                            next.exchange(
                                                                    ClientRequest.from(request)
                                                                            .headers(
                                                                                    h ->
                                                                                            h.set(
                                                                                                    HttpHeaders
                                                                                                            .AUTHORIZATION,
                                                                                                    "Bearer "
                                                                                                            + fresh))
                                                                            .build()));
                                });
    }
}
