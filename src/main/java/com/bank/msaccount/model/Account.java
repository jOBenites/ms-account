package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad base que representa una cuenta bancaria (producto pasivo).
 * Cada cuenta tiene un numero unico, un cliente titular y un saldo.
 * Las subclases {@link SavingsAccount}, {@link CheckingAccount} y
 * {@link FixedTermAccount} agregan atributos especificos de cada tipo.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "account")
public abstract class Account {

    /** Tipo de cuenta de ahorro. */
    public static final String TYPE_SAVINGS = "SAVINGS";
    /** Tipo de cuenta corriente. */
    public static final String TYPE_CHECKING = "CHECKING";
    /** Tipo de cuenta a plazo fijo. */
    public static final String TYPE_FIXED_TERM = "FIXED_TERM";

    @Id
    private String id;

    @Indexed(unique = true)
    private String accountNumber;

    @Indexed
    private String customerId;

    private String accountType;

    private BigDecimal balance;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Constructor completo para la creacion de una cuenta.
     * El saldo inicial de toda cuenta es cero.
     *
     * @param customerId identificador del cliente que abre la cuenta
     * @param accountNumber numero de cuenta unico generado
     * @param accountType tipo de cuenta (SAVINGS, CHECKING o FIXED_TERM)
     */
    protected Account(String customerId, String accountNumber, String accountType) {
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.balance = BigDecimal.ZERO;
    }
}
