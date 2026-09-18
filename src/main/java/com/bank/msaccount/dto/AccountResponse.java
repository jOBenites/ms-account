package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de respuesta generico para cualquier tipo de cuenta.
 * Contiene los campos comunes y, segun el tipo: titulares y firmantes
 * (corriente), limite de movimientos mensuales y promedio diario minimo
 * (ahorro), y dia permitido para movimientos (plazo fijo).
 */
@Getter
@Setter
public class AccountResponse {

    private String id;
    private String accountNumber;
    private String customerId;
    private String accountType;
    private BigDecimal balance;
    private List<String> holderIds;
    private List<String> signerIds;
    private Boolean commissionFree;
    private Integer monthlyMovementLimit;
    private BigDecimal minimumDailyAverage;
    private Integer allowedDayOfMonth;
}
