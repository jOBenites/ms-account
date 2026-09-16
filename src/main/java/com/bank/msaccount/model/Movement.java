package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento financiero registrado sobre una cuenta bancaria.
 * Tipos: deposito (DEPOSIT) y retiro (WITHDRAWAL). Cada movimiento queda
 * asociado a su cuenta y se publica como evento bank.movement.recorded.
 * Incluye comision si la cuenta excede el limite de transacciones gratuitas.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "movement")
public class Movement {

    /** Tipo de movimiento de deposito. */
    public static final String TYPE_DEPOSIT = "DEPOSIT";
    /** Tipo de movimiento de retiro. */
    public static final String TYPE_WITHDRAWAL = "WITHDRAWAL";

    @Id
    private String id;

    @Indexed
    private String accountId;

    private String movementType;

    private BigDecimal amount;

    private BigDecimal commission;

    private LocalDateTime occurredAt;

    /**
     * Constructor para crear un movimiento nuevo.
     * La fecha de ocurrencia se fija al momento actual.
     * La comision inicia en cero por defecto.
     *
     * @param accountId identificador de la cuenta afectada
     * @param movementType tipo de movimiento (DEPOSIT o WITHDRAWAL)
     * @param amount monto del movimiento (mayor a cero)
     */
    public Movement(String accountId, String movementType, BigDecimal amount) {
        this(accountId, movementType, amount, BigDecimal.ZERO);
    }

    /**
     * Constructor para crear un movimiento nuevo con comision.
     *
     * @param accountId identificador de la cuenta afectada
     * @param movementType tipo de movimiento (DEPOSIT o WITHDRAWAL)
     * @param amount monto del movimiento (mayor a cero)
     * @param commission comision cobrada por el movimiento
     */
    public Movement(String accountId, String movementType, BigDecimal amount, BigDecimal commission) {
        this.accountId = accountId;
        this.movementType = movementType;
        this.amount = amount;
        this.commission = commission;
        this.occurredAt = LocalDateTime.now();
    }
}
