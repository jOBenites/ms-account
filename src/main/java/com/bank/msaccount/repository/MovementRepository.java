package com.bank.msaccount.repository;

import com.bank.msaccount.model.Movement;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Repositorio reactivo para la entidad Movement en MongoDB.
 * No se permite @Query ni consultas dinamicas segun las reglas del proyecto.
 */
public interface MovementRepository extends ReactiveMongoRepository<Movement, String> {

    /**
     * Cuenta los movimientos de una cuenta dentro de un rango de fechas.
     * Se usa para validar el limite mensual de movimientos del ahorro
     * y la regla de un solo movimiento mensual del plazo fijo.
     *
     * @param accountId identificador de la cuenta
     * @param from inicio del rango (inclusive)
     * @param to fin del rango (inclusive)
     * @return Mono con el numero de movimientos en el rango
     */
    Mono<Long> countByAccountIdAndOccurredAtBetween(String accountId, LocalDateTime from, LocalDateTime to);

    /**
     * Lista los movimientos de una cuenta del mas reciente al mas antiguo.
     *
     * @param accountId identificador de la cuenta
     * @return Flux de movimientos ordenados por fecha descendente
     */
    Flux<Movement> findByAccountIdOrderByOccurredAtDesc(String accountId);
}
