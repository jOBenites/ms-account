package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cuenta a plazo fijo.
 * Exclusiva de clientes personales, sin limite de cuentas por cliente.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "account")
public class FixedTermAccount extends Account {

    /**
     * Constructor para crear una cuenta a plazo fijo.
     *
     * @param customerId identificador del cliente titular
     * @param accountNumber numero de cuenta unico
     */
    public FixedTermAccount(String customerId, String accountNumber) {
        super(customerId, accountNumber, TYPE_FIXED_TERM);
    }
}
