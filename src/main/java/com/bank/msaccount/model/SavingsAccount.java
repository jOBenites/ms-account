package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cuenta de ahorro.
 * Exclusiva de clientes personales, maximo una por cliente.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "account")
public class SavingsAccount extends Account {

    /**
     * Constructor para crear una cuenta de ahorro.
     *
     * @param customerId identificador del cliente titular
     * @param accountNumber numero de cuenta unico
     */
    public SavingsAccount(String customerId, String accountNumber) {
        super(customerId, accountNumber, TYPE_SAVINGS);
    }
}
