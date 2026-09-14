package com.bank.msaccount.event;

import com.bank.msaccount.model.CustomerView;
import com.bank.msaccount.repository.CustomerViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link CustomerViewConsumer}.
 * Valida el upsert idempotente de la vista local de clientes.
 */
@ExtendWith(MockitoExtension.class)
class CustomerViewConsumerTest {

    @Mock
    private CustomerViewRepository customerViewRepository;

    @InjectMocks
    private CustomerViewConsumer customerViewConsumer;

    @Test
    void onCustomerCreated_newCustomer_savesView() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.empty());

        Map<String, Object> payload = Map.of(
                "customerId", "cust-1",
                "customerType", "PERSONAL",
                "profile", "REGULAR",
                "documentNumber", "12345678"
        );
        customerViewConsumer.onCustomerCreated(payload);

        ArgumentCaptor<CustomerView> captor = ArgumentCaptor.forClass(CustomerView.class);
        verify(customerViewRepository).save(captor.capture());
        CustomerView saved = captor.getValue();
        assertEquals("cust-1", saved.getCustomerId());
        assertEquals("PERSONAL", saved.getCustomerType());
        assertEquals("REGULAR", saved.getProfile());
        assertEquals("12345678", saved.getDocumentNumber());
    }

    @Test
    void onCustomerCreated_existingCustomer_updatesView() {
        CustomerView existing = new CustomerView("cust-1", "PERSONAL", "REGULAR", "12345678");
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.of(existing));

        Map<String, Object> payload = Map.of(
                "customerId", "cust-1",
                "customerType", "PERSONAL",
                "profile", "VIP",
                "documentNumber", "12345678"
        );
        customerViewConsumer.onCustomerCreated(payload);

        ArgumentCaptor<CustomerView> captor = ArgumentCaptor.forClass(CustomerView.class);
        verify(customerViewRepository).save(captor.capture());
        assertEquals("VIP", captor.getValue().getProfile());
    }
}
