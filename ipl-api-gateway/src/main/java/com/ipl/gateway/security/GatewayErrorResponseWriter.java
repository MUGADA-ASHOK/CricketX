package com.ipl.gateway.security;

import com.ipl.gateway.dto.ErrorResponse;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/**
 * Shared JSON error body writer for the 401 and 403 handlers, so both
 * produce the exact same response shape without duplicating the
 * serialization logic.
 */
final class GatewayErrorResponseWriter {

    private GatewayErrorResponseWriter() {
    }

    static Mono<Void> write(
            ServerWebExchange exchange,
            JsonMapper jsonMapper,
            HttpStatus status,
            String error,
            String message) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getPath().value();

        ErrorResponse body =
                new ErrorResponse(status.value(), error, message, path);

        byte[] bytes;

        try {
            bytes = jsonMapper.writeValueAsBytes(body);
        } catch (JacksonException ex) {
            // Should never happen for this simple record, but never let a
            // serialization failure leak a stack trace to the client.
            bytes = (
                    "{\"status\":" + status.value() +
                            ",\"error\":\"" + error + "\"}"
            ).getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        return response.writeWith(Mono.just(buffer));
    }
}