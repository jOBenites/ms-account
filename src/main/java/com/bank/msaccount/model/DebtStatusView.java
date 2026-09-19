package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Vista de lectura local del estado de deuda vencida del cliente.
 * Alimentada por los eventos bank.debt.overdue-detected y bank.debt.settled.
 * Se usa para bloquear la adquisicion de nuevos productos cuando el cliente
 * tiene deuda vencida en cualquier producto de credito.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "debt_status_view")
public class DebtStatusView {

    @Id
    private String customerId;

    private boolean hasOverdueDebt;

    private String creditProductId;

    private LocalDateTime detectedAt;

    /**
     * Constructor completo de la vista de deuda.
     *
     * @param customerId identificador del cliente
     * @param hasOverdueDebt true si tiene deuda vencida
     * @param creditProductId identificador del producto con deuda vencida
     * @param detectedAt fecha y hora en que se detecto la deuda
     */
    public DebtStatusView(String customerId, boolean hasOverdueDebt,
                          String creditProductId, LocalDateTime detectedAt) {
        this.customerId = customerId;
        this.hasOverdueDebt = hasOverdueDebt;
        this.creditProductId = creditProductId;
        this.detectedAt = detectedAt;
    }
}
