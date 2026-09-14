package com.bank.msaccount.event;

import com.bank.msaccount.model.Account;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Productor de eventos Kafka para el dominio account.
 * Publica bank.account.opened cuando se abre una nueva cuenta.
 */
@Component
@RequiredArgsConstructor
public class AccountEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AccountEventProducer.class);
    private static final String ACCOUNT_OPENED_TOPIC = "bank.account.opened";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publica el evento bank.account.opened con los datos minimos de la cuenta.
     *
     * @param account cuenta recien abierta
     */
    public void publishAccountOpened(Account account) {
        Map<String, Object> payload = Map.of(
                "accountId", account.getId(),
                "customerId", account.getCustomerId(),
                "accountType", account.getAccountType(),
                "occurredAt", LocalDateTime.now()
        );
        kafkaTemplate.send(ACCOUNT_OPENED_TOPIC, account.getId(), payload);
        log.info("Evento bank.account.opened publicado para cuenta {}", account.getId());
    }
}
