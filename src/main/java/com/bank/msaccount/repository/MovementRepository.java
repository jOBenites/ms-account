package com.bank.msaccount.repository;

import com.bank.msaccount.model.Movement;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repositorio para la entidad Movement en MongoDB.
 * No se permite @Query ni consultas dinamicas segun las reglas del proyecto.
 */
public interface MovementRepository extends MongoRepository<Movement, String> {

    /**
     * Cuenta los movimientos de una cuenta dentro de un rango de fechas.
     * Se usa para validar el limite mensual de movimientos del ahorro
     * y la regla de un solo movimiento mensual del plazo fijo.
     *
     * @param accountId identificador de la cuenta
     * @param from inicio del rango (inclusive)
     * @param to fin del rango (inclusive)
     * @return numero de movimientos de la cuenta en el rango
     */
    long countByAccountIdAndOccurredAtBetween(String accountId, LocalDateTime from, LocalDateTime to);

    /**
     * Lista los movimientos de una cuenta del mas reciente al mas antiguo.
     *
     * @param accountId identificador de la cuenta
     * @return lista de movimientos ordenada por fecha descendente
     */
    List<Movement> findByAccountIdOrderByOccurredAtDesc(String accountId);
}
