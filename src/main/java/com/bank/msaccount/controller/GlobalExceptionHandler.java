package com.bank.msaccount.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Manejador global de excepciones del controlador reactivo.
 * Traduce las violaciones de reglas de negocio a respuestas HTTP 400.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maneja las IllegalArgumentException lanzadas por las validaciones de negocio.
     *
     * @param ex excepcion lanzada por el servicio
     * @return Mono con respuesta 400 y el mensaje de la regla violada
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Map<String, String>>> handleIllegalArgument(IllegalArgumentException ex) {
        return Mono.just(ResponseEntity.badRequest().body(Map.of("error", ex.getMessage())));
    }
}
