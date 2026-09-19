package com.bank.msaccount.event;

import com.bank.msaccount.model.DebtStatusView;
import com.bank.msaccount.repository.DebtStatusViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link DebtStatusViewConsumer}.
 * Valida la actualizacion de la vista de deuda vencida.
 */
@ExtendWith(MockitoExtension.class)
class DebtStatusViewConsumerTest {

    @Mock
    private DebtStatusViewRepository debtStatusViewRepository;

    @InjectMocks
    private DebtStatusViewConsumer debtStatusViewConsumer;

    @Test
    void onOverdueDetected_newCustomer_createsViewWithOverdueDebt() {
        when(debtStatusViewRepository.findById("cust-1")).thenReturn(Mono.empty());
        when(debtStatusViewRepository.save(any(DebtStatusView.class))).thenReturn(Mono.just(new DebtStatusView()));

        Map<String, Object> payload = Map.of(
                "customerId", "cust-1",
                "creditProductId", "credit-1",
                "occurredAt", "2026-09-19T10:00:00"
        );
        debtStatusViewConsumer.onOverdueDetected(payload);

        ArgumentCaptor<DebtStatusView> captor = ArgumentCaptor.forClass(DebtStatusView.class);
        verify(debtStatusViewRepository).save(captor.capture());
        assertEquals("cust-1", captor.getValue().getCustomerId());
        assertTrue(captor.getValue().isHasOverdueDebt());
        assertEquals("credit-1", captor.getValue().getCreditProductId());
    }

    @Test
    void onOverdueDetected_existingCustomer_updatesView() {
        DebtStatusView existing = new DebtStatusView("cust-1", false, null, null);
        when(debtStatusViewRepository.findById("cust-1")).thenReturn(Mono.just(existing));
        when(debtStatusViewRepository.save(any(DebtStatusView.class))).thenReturn(Mono.just(existing));

        Map<String, Object> payload = Map.of(
                "customerId", "cust-1",
                "creditProductId", "credit-1",
                "occurredAt", "2026-09-19T10:00:00"
        );
        debtStatusViewConsumer.onOverdueDetected(payload);

        ArgumentCaptor<DebtStatusView> captor = ArgumentCaptor.forClass(DebtStatusView.class);
        verify(debtStatusViewRepository).save(captor.capture());
        assertTrue(captor.getValue().isHasOverdueDebt());
    }

    @Test
    void onSettled_existingCustomer_clearsOverdueDebt() {
        DebtStatusView existing = new DebtStatusView("cust-1", true, "credit-1", null);
        when(debtStatusViewRepository.findById("cust-1")).thenReturn(Mono.just(existing));
        when(debtStatusViewRepository.save(any(DebtStatusView.class))).thenReturn(Mono.just(existing));

        Map<String, Object> payload = Map.of(
                "customerId", "cust-1",
                "creditProductId", "credit-1",
                "occurredAt", "2026-09-19T10:00:00"
        );
        debtStatusViewConsumer.onSettled(payload);

        ArgumentCaptor<DebtStatusView> captor = ArgumentCaptor.forClass(DebtStatusView.class);
        verify(debtStatusViewRepository).save(captor.capture());
        assertFalse(captor.getValue().isHasOverdueDebt());
    }
}
