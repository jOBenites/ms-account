package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO de solicitud para registrar un deposito o retiro sobre una cuenta.
 */
@Getter
@Setter
public class MovementRequest {

    private BigDecimal amount;
}
