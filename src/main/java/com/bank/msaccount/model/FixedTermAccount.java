package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cuenta a plazo fijo.
 * Exclusiva de clientes personales, sin limite de cuentas por cliente.
 * Solo permite un movimiento (deposito o retiro) por mes, y unicamente
 * en el dia del mes configurado ({@code allowedDayOfMonth}).
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "account")
public class FixedTermAccount extends Account {

    /** Dia del mes permitido por defecto. */
    public static final int DEFAULT_ALLOWED_DAY_OF_MONTH = 1;

    private Integer allowedDayOfMonth;

    /**
     * Constructor para crear una cuenta a plazo fijo con el dia por defecto.
     *
     * @param customerId identificador del cliente titular
     * @param accountNumber numero de cuenta unico
     */
    public FixedTermAccount(String customerId, String accountNumber) {
        this(customerId, accountNumber, null);
    }

    /**
     * Constructor para crear una cuenta a plazo fijo con dia configurado.
     *
     * @param customerId identificador del cliente titular
     * @param accountNumber numero de cuenta unico
     * @param allowedDayOfMonth dia del mes en que se permiten movimientos
     *        (nullable, por defecto 1)
     */
    public FixedTermAccount(String customerId, String accountNumber, Integer allowedDayOfMonth) {
        super(customerId, accountNumber, TYPE_FIXED_TERM);
        this.allowedDayOfMonth = allowedDayOfMonth == null
                ? DEFAULT_ALLOWED_DAY_OF_MONTH : allowedDayOfMonth;
    }
}
