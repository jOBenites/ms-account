package com.bank.msaccount.service;

import com.bank.msaccount.event.AccountEventProducer;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.Movement;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.repository.AccountRepository;
import com.bank.msaccount.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link MovementService}.
 * Valida las reglas de movimientos de ahorro (tope mensual),
 * plazo fijo (dia y un solo movimiento), saldo insuficiente
 * y la publicacion de eventos bank.movement.recorded.
 */
@ExtendWith(MockitoExtension.class)
class MovementServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MovementRepository movementRepository;

    @Mock
    private AccountEventProducer accountEventProducer;

    @InjectMocks
    private MovementService movementService;

    private SavingsAccount savingsAccount;
    private CheckingAccount checkingAccount;
    private Movement depositMovement;
    private YearMonth currentMonth;
    private LocalDateTime monthStart;
    private LocalDateTime monthEnd;

    @BeforeEach
    void setUp() {
        movementService.setFreeMonthlyTransactions(SavingsAccount.DEFAULT_MONTHLY_MOVEMENT_LIMIT);
        movementService.setTransactionCommission(BigDecimal.ZERO);

        savingsAccount = new SavingsAccount("cust-1", "100000000001");
        savingsAccount.setId("acc-1");

        checkingAccount = new CheckingAccount("cust-2", "100000000002", List.of("cust-2"), List.of());
        checkingAccount.setId("acc-2");

        depositMovement = new Movement("acc-1", Movement.TYPE_DEPOSIT, new BigDecimal("100.00"));
        depositMovement.setId("mov-1");

        currentMonth = YearMonth.now();
        monthStart = currentMonth.atDay(1).atStartOfDay();
        monthEnd = currentMonth.atEndOfMonth().atTime(LocalTime.MAX);
    }

    @Test
    void deposit_success() {
        when(accountRepository.findById("acc-1")).thenReturn(Mono.just(savingsAccount));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(depositMovement));

        StepVerifier.create(movementService.deposit("acc-1", new BigDecimal("100.00")))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(new BigDecimal("100.00"), savingsAccount.getBalance());
                })
                .verifyComplete();

        verify(accountEventProducer).publishMovementRecorded(depositMovement, Account.TYPE_SAVINGS);
    }

    @Test
    void deposit_accountNotFound_returnsEmpty() {
        when(accountRepository.findById("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(movementService.deposit("nonexistent", new BigDecimal("10.00")))
                .verifyComplete();

        verify(movementRepository, never()).save(any());
    }

    @Test
    void deposit_nonPositiveAmount_throws() {
        StepVerifier.create(movementService.deposit("acc-1", BigDecimal.ZERO))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(movementService.deposit("acc-1", new BigDecimal("-10")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void deposit_savingsExceedsFreeLimit_appliesCommission() {
        movementService.setFreeMonthlyTransactions(3);
        movementService.setTransactionCommission(new BigDecimal("0.50"));
        when(accountRepository.findById("acc-1")).thenReturn(Mono.just(savingsAccount));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(3L));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(depositMovement));

        StepVerifier.create(movementService.deposit("acc-1", new BigDecimal("100.00")))
                .assertNext(result -> {
                    assertNotNull(result);
                    assertEquals(new BigDecimal("99.50"), savingsAccount.getBalance());
                })
                .verifyComplete();
    }

    @Test
    void deposit_fixedTermOnAllowedDay_success() {
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003",
                LocalDate.now().getDayOfMonth());
        fixedTerm.setId("acc-3");
        Movement fixedDeposit = new Movement("acc-3", Movement.TYPE_DEPOSIT, new BigDecimal("200.00"));
        when(accountRepository.findById("acc-3")).thenReturn(Mono.just(fixedTerm));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-3"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(fixedDeposit));

        StepVerifier.create(movementService.deposit("acc-3", new BigDecimal("200.00")))
                .assertNext(result -> assertEquals(new BigDecimal("200.00"), fixedTerm.getBalance()))
                .verifyComplete();
    }

    @Test
    void deposit_fixedTermOnWrongDay_throws() {
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003",
                LocalDate.now().plusDays(1).getDayOfMonth());
        fixedTerm.setId("acc-3");
        when(accountRepository.findById("acc-3")).thenReturn(Mono.just(fixedTerm));

        StepVerifier.create(movementService.deposit("acc-3", new BigDecimal("200.00")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(movementRepository, never()).save(any());
    }

    @Test
    void deposit_fixedTermSecondMovementSameMonth_throws() {
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003",
                LocalDate.now().getDayOfMonth());
        fixedTerm.setId("acc-3");
        when(accountRepository.findById("acc-3")).thenReturn(Mono.just(fixedTerm));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-3"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(movementService.deposit("acc-3", new BigDecimal("200.00")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(movementRepository, never()).save(any());
    }

    @Test
    void withdraw_success() {
        savingsAccount.setBalance(new BigDecimal("500.00"));
        Movement withdrawal = new Movement("acc-1", Movement.TYPE_WITHDRAWAL, new BigDecimal("100.00"));
        withdrawal.setId("mov-2");
        when(accountRepository.findById("acc-1")).thenReturn(Mono.just(savingsAccount));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.save(any(Movement.class))).thenReturn(Mono.just(withdrawal));

        StepVerifier.create(movementService.withdraw("acc-1", new BigDecimal("100.00")))
                .assertNext(result -> assertEquals(new BigDecimal("400.00"), savingsAccount.getBalance()))
                .verifyComplete();
    }

    @Test
    void withdraw_insufficientBalance_throws() {
        savingsAccount.setBalance(new BigDecimal("50.00"));
        when(accountRepository.findById("acc-1")).thenReturn(Mono.just(savingsAccount));

        StepVerifier.create(movementService.withdraw("acc-1", new BigDecimal("100.00")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(movementRepository, never()).save(any());
    }

    @Test
    void withdraw_accountNotFound_returnsEmpty() {
        when(accountRepository.findById("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(movementService.withdraw("nonexistent", new BigDecimal("10.00")))
                .verifyComplete();
    }

    @Test
    void findMovements_accountNotFound_returnsEmpty() {
        when(accountRepository.existsById("nonexistent")).thenReturn(Mono.just(false));

        StepVerifier.create(movementService.findMovements("nonexistent"))
                .verifyComplete();

        verify(movementRepository, never()).findByAccountIdOrderByOccurredAtDesc(anyString());
    }

    @Test
    void findMovements_accountExists_returnsMovements() {
        when(accountRepository.existsById("acc-1")).thenReturn(Mono.just(true));
        when(movementRepository.findByAccountIdOrderByOccurredAtDesc("acc-1"))
                .thenReturn(Flux.just(depositMovement));

        StepVerifier.create(movementService.findMovements("acc-1"))
                .assertNext(movement -> assertEquals("mov-1", movement.getId()))
                .verifyComplete();
    }

    @Test
    void toMovementResponse_mapsFields() {
        var response = movementService.toMovementResponse(depositMovement);

        assertEquals("mov-1", response.getId());
        assertEquals("acc-1", response.getAccountId());
        assertEquals(Movement.TYPE_DEPOSIT, response.getMovementType());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertNotNull(response.getOccurredAt());
    }

    @Test
    void toMovementResponseList_mapsList() {
        Movement second = new Movement("acc-1", Movement.TYPE_WITHDRAWAL, new BigDecimal("50.00"));
        second.setId("mov-2");

        StepVerifier.create(movementService.toMovementResponseList(Flux.just(depositMovement, second)))
                .expectNextCount(2)
                .verifyComplete();
    }
}
