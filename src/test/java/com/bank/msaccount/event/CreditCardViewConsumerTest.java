package com.bank.msaccount.event;

import com.bank.msaccount.model.CustomerView;
import com.bank.msaccount.repository.CustomerViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link CreditCardViewConsumer}.
 * Valida que el evento bank.creditcard.issued marque hasCreditCard en la vista local.
 */
@ExtendWith(MockitoExtension.class)
class CreditCardViewConsumerTest {

    @Mock
    private CustomerViewRepository customerViewRepository;

    @InjectMocks
    private CreditCardViewConsumer creditCardViewConsumer;

    @Test
    void onCreditCardIssued_existingCustomer_setsHasCreditCard() {
        CustomerView existing = new CustomerView("cust-1", "PERSONAL", "VIP", "12345678");
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(existing));
        when(customerViewRepository.save(any(CustomerView.class))).thenReturn(Mono.just(existing));

        Map<String, Object> payload = Map.of(
                "cardId", "card-1",
                "customerId", "cust-1",
                "cardType", "PERSONAL",
                "creditLimit", 5000.00
        );
        creditCardViewConsumer.onCreditCardIssued(payload);

        ArgumentCaptor<CustomerView> captor = ArgumentCaptor.forClass(CustomerView.class);
        verify(customerViewRepository).save(captor.capture());
        assertTrue(captor.getValue().getHasCreditCard());
        assertEquals("cust-1", captor.getValue().getCustomerId());
    }

    @Test
    void onCreditCardIssued_newCustomer_createsViewWithHasCreditCard() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Mono.empty());
        when(customerViewRepository.save(any(CustomerView.class))).thenReturn(Mono.just(new CustomerView()));

        Map<String, Object> payload = Map.of(
                "cardId", "card-2",
                "customerId", "cust-2",
                "cardType", "BUSINESS",
                "creditLimit", 10000.00
        );
        creditCardViewConsumer.onCreditCardIssued(payload);

        ArgumentCaptor<CustomerView> captor = ArgumentCaptor.forClass(CustomerView.class);
        verify(customerViewRepository).save(captor.capture());
        assertTrue(captor.getValue().getHasCreditCard());
        assertEquals("cust-2", captor.getValue().getCustomerId());
    }
}
