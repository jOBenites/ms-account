package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO de solicitud para la apertura de cualquier tipo de cuenta.
 * Los titulares y firmantes solo aplican a cuentas corrientes.
 */
@Getter
@Setter
public class OpenAccountRequest {

    private String customerId;
    private List<String> holderIds;
    private List<String> signerIds;
}
