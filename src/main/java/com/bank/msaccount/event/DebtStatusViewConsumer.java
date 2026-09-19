package com.bank.msaccount.event;

import com.bank.msaccount.model.DebtStatusView;
import com.bank.msaccount.repository.DebtStatusViewRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consumidor de eventos Kafka del dominio debt.
 * Mantiene actualizada la vista de deuda vencida en la base local,
 * usada para bloquear la adquisicion de nuevos productos.
 */
@Component
@RequiredArgsConstructor
public class DebtStatusViewConsumer {

    private static final Logger log = LoggerFactory.getLogger(DebtStatusViewConsumer.class);

    private final DebtStatusViewRepository debtStatusViewRepository;

    /**
     * Consume bank.debt.overdue-detected y marca al cliente con deuda vencida.
     * Si el cliente aun no tiene vista, la crea.
     *
     * @param payload datos del evento (customerId, creditProductId, occurredAt)
     */
    @KafkaListener(topics = "bank.debt.overdue-detected", groupId = "ms-account")
    public void onOverdueDetected(Map<String, Object> payload) {
        String customerId = (String) payload.get("customerId");
        String creditProductId = (String) payload.get("creditProductId");
        debtStatusViewRepository.findById(customerId)
                .defaultIfEmpty(new DebtStatusView())
                .map(view -> {
                    view.setCustomerId(customerId);
                    view.setHasOverdueDebt(true);
                    view.setCreditProductId(creditProductId);
                    view.setDetectedAt(LocalDateTime.now());
                    return view;
                })
                .flatMap(debtStatusViewRepository::save)
                .subscribe(v -> log.info("Deuda vencida marcada para cliente {} (producto {})", customerId, creditProductId));
    }

    /**
     * Consume bank.debt.settled y desmarca la deuda vencida del cliente.
     *
     * @param payload datos del evento (customerId, creditProductId, occurredAt)
     */
    @KafkaListener(topics = "bank.debt.settled", groupId = "ms-account")
    public void onSettled(Map<String, Object> payload) {
        String customerId = (String) payload.get("customerId");
        debtStatusViewRepository.findById(customerId)
                .flatMap(view -> {
                    view.setHasOverdueDebt(false);
                    view.setCreditProductId(null);
                    return debtStatusViewRepository.save(view);
                })
                .subscribe(v -> log.info("Deuda vencida liquidada para cliente {}", customerId));
    }
}
