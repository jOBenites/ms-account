package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de solicitud para la apertura de cualquier tipo de cuenta.
 * Los titulares y firmantes solo aplican a cuentas corrientes.
 * El dia permitido para movimientos solo aplica a cuentas a plazo fijo.
 * El saldo inicial debe respetar el monto minimo de apertura configurado.
 */
@Getter
@Setter
public class OpenAccountRequest {

    private String customerId;
    private List<String> holderIds;
    private List<String> signerIds;
    private Integer allowedDayOfMonth;
    private BigDecimal initialBalance;
}
