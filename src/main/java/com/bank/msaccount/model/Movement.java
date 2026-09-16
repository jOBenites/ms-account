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

    private LocalDateTime occurredAt;

    /**
     * Constructor para crear un movimiento nuevo.
     * La fecha de ocurrencia se fija al momento actual.
     *
     * @param accountId identificador de la cuenta afectada
     * @param movementType tipo de movimiento (DEPOSIT o WITHDRAWAL)
     * @param amount monto del movimiento (mayor a cero)
     */
    public Movement(String accountId, String movementType, BigDecimal amount) {
        this.accountId = accountId;
        this.movementType = movementType;
        this.amount = amount;
        this.occurredAt = LocalDateTime.now();
    }
}
