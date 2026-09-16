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
 * Consumidor de eventos Kafka del dominio customer.
 * Mantiene actualizada la vista de lectura local de clientes,
 * usada para validar tipo y perfil al abrir cuentas.
 */
@Component
@RequiredArgsConstructor
public class CustomerViewConsumer {

    private static final Logger log = LoggerFactory.getLogger(CustomerViewConsumer.class);

    private final CustomerViewRepository customerViewRepository;

    /**
     * Consume bank.customer.created y hace upsert de la vista local por customerId,
     * de modo que la reentrega del evento sea idempotente.
     *
     * @param payload datos del evento (customerId, customerType, profile, documentNumber)
     */
    @KafkaListener(topics = "bank.customer.created", groupId = "ms-account")
    public void onCustomerCreated(Map<String, Object> payload) {
        String customerId = (String) payload.get("customerId");
        customerViewRepository.findById(customerId)
                .defaultIfEmpty(new CustomerView())
                .map(view -> {
                    view.setCustomerId(customerId);
                    view.setCustomerType((String) payload.get("customerType"));
                    view.setProfile((String) payload.get("profile"));
                    view.setDocumentNumber((String) payload.get("documentNumber"));
                    return view;
                })
                .flatMap(customerViewRepository::save)
                .subscribe(v -> log.info("Vista local de cliente {} actualizada", customerId));
    }
}
