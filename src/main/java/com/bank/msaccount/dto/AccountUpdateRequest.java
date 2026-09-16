package com.bank.msaccount.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO de solicitud para actualizar una cuenta.
 * Solo aplica a cuentas corrientes (titulares y firmantes).
 */
@Getter
@Setter
public class AccountUpdateRequest {

    private List<String> holderIds;
    private List<String> signerIds;
}
