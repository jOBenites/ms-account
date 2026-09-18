package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO de solicitud para transferencias entre cuentas del mismo banco.
 * Puede ser entre cuentas propias o a terceros.
 */
@Getter
@Setter
public class TransferRequest {

    private String sourceAccountId;
    private String targetAccountId;
    private BigDecimal amount;
}
