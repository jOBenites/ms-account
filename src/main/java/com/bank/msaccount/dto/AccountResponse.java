package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO de respuesta generico para cualquier tipo de cuenta.
 * Contiene los campos comunes y, solo para cuentas corrientes,
 * los titulares y firmantes autorizados.
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
}
