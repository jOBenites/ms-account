package com.bank.msaccount.event;

import com.bank.msaccount.cache.CustomerViewCacheService;
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
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private CustomerViewCacheService customerViewCacheService;

    @InjectMocks
    private CustomerViewConsumer customerViewConsumer;

    @Test
    void onCustomerCreated_newCustomer_savesView() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.empty());
        when(customerViewRepository.save(any(CustomerView.class))).thenReturn(Mono.just(new CustomerView()));
        when(customerViewCacheService.put(any(CustomerView.class))).thenReturn(Mono.empty());

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
        verify(customerViewCacheService).put(any(CustomerView.class));
    }

    @Test
    void onCustomerCreated_existingCustomer_updatesView() {
        CustomerView existing = new CustomerView("cust-1", "PERSONAL", "REGULAR", "12345678");
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(existing));
        when(customerViewRepository.save(any(CustomerView.class))).thenReturn(Mono.just(existing));
        when(customerViewCacheService.put(any(CustomerView.class))).thenReturn(Mono.empty());

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
        verify(customerViewCacheService).put(any(CustomerView.class));
    }
}
