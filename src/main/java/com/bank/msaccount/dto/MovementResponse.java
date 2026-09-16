package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO de respuesta de un movimiento registrado sobre una cuenta.
 */
@Getter
@Setter
public class MovementResponse {

    private String id;
    private String accountId;
    private String movementType;
    private BigDecimal amount;
    private LocalDateTime occurredAt;
}
