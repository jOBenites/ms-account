package com.bank.msaccount.event;

import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.Movement;
import com.bank.msaccount.model.SavingsAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Pruebas unitarias para {@link AccountEventProducer}.
 * Verifica la publicacion de los eventos bank.account.opened y
 * bank.movement.recorded.
 */
@ExtendWith(MockitoExtension.class)
class AccountEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private AccountEventProducer accountEventProducer;

    @Test
    void publishAccountOpened_sendsCorrectPayload() {
        SavingsAccount account = new SavingsAccount("cust-1", "100000000001");
        account.setId("acc-1");

        accountEventProducer.publishAccountOpened(account);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("acc-1"), payloadCaptor.capture());

        assertEquals("bank.account.opened", topicCaptor.getValue());
        assertEquals("acc-1", payloadCaptor.getValue().get("accountId"));
        assertEquals("cust-1", payloadCaptor.getValue().get("customerId"));
        assertEquals(Account.TYPE_SAVINGS, payloadCaptor.getValue().get("accountType"));
    }

    @Test
    void publishMovementRecorded_sendsCorrectPayload() {
        Movement movement = new Movement("acc-1", Movement.TYPE_DEPOSIT, new BigDecimal("500.00"));
        movement.setId("mov-1");

        accountEventProducer.publishMovementRecorded(movement, Account.TYPE_SAVINGS);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), eq("mov-1"), payloadCaptor.capture());

        assertEquals("bank.movement.recorded", topicCaptor.getValue());
        assertEquals("mov-1", payloadCaptor.getValue().get("movementId"));
        assertEquals("acc-1", payloadCaptor.getValue().get("productId"));
        assertEquals(Account.TYPE_SAVINGS, payloadCaptor.getValue().get("productType"));
        assertEquals(Movement.TYPE_DEPOSIT, payloadCaptor.getValue().get("movementType"));
        assertEquals(new BigDecimal("500.00"), payloadCaptor.getValue().get("amount"));
    }
}
