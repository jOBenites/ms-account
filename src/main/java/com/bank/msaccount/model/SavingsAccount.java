package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cuenta de ahorro.
 * Exclusiva de clientes personales, maximo una por cliente.
 * Tiene un tope duro de movimientos mensuales: superado el limite,
 * se rechaza cualquier deposito o retiro adicional en el mes.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "account")
public class SavingsAccount extends Account {

    /** Limite de movimientos mensuales por defecto. */
    public static final int DEFAULT_MONTHLY_MOVEMENT_LIMIT = 5;

    private Integer monthlyMovementLimit;

    /**
     * Constructor para crear una cuenta de ahorro.
     * El limite de movimientos mensuales inicia en el valor por defecto.
     *
     * @param customerId identificador del cliente titular
     * @param accountNumber numero de cuenta unico
     */
    public SavingsAccount(String customerId, String accountNumber) {
        super(customerId, accountNumber, TYPE_SAVINGS);
        this.monthlyMovementLimit = DEFAULT_MONTHLY_MOVEMENT_LIMIT;
    }
}
