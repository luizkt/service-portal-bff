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

import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * Limita o {@code block()} das chamadas aos backends. O timeout de resposta do Netty dispara
 * primeiro; este prazo (resposta + folga) é a rede de segurança para a chamada inteira.
 */
final class HttpTimeouts {

    /** Folga somada ao timeout de resposta para o prazo total da chamada. */
    private static final Duration GRACE = Duration.ofSeconds(2);

    private HttpTimeouts() {}

    static Duration deadline(long responseTimeoutMs) {
        return Duration.ofMillis(responseTimeoutMs).plus(GRACE);
    }

    /** Fallback do {@code Mono.timeout}: falha com {@link BackendTimeoutException}. */
    static <T> Mono<T> onTimeout(Duration deadline) {
        return Mono.error(
                () ->
                        new BackendTimeoutException(
                                "Backend não respondeu em " + deadline.toMillis() + " ms"));
    }
}
