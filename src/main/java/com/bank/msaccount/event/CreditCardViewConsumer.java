package com.bank.msaccount.event;

import com.bank.msaccount.model.CustomerView;
import com.bank.msaccount.repository.CustomerViewRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumidor de eventos Kafka del dominio creditcard.
 * Actualiza la vista local de cliente marcando que posee tarjeta de credito,
 * usada para validar requisitos de perfil VIP al abrir cuentas de ahorro.
 */
@Component
@RequiredArgsConstructor
public class CreditCardViewConsumer {

    private static final Logger log = LoggerFactory.getLogger(CreditCardViewConsumer.class);

    private final CustomerViewRepository customerViewRepository;

    /**
     * Consume bank.creditcard.issued y marca hasCreditCard en la vista local.
     * Si el cliente aun no tiene vista, la crea con datos minimos.
     *
     * @param payload datos del evento (cardId, customerId, cardType, creditLimit)
     */
    @KafkaListener(topics = "bank.creditcard.issued", groupId = "ms-account")
    public void onCreditCardIssued(Map<String, Object> payload) {
        String customerId = (String) payload.get("customerId");
        customerViewRepository.findById(customerId)
                .defaultIfEmpty(new CustomerView(customerId, null, null, null))
                .map(view -> {
                    view.setHasCreditCard(true);
                    return view;
                })
                .flatMap(customerViewRepository::save)
                .subscribe(v -> log.info("Vista local de cliente {} actualizada: tiene tarjeta de credito", customerId));
    }
}
