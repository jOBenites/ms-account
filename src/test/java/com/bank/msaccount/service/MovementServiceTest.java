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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(savingsAccount));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-1"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any(Movement.class))).thenReturn(depositMovement);

        Optional<Movement> result = movementService.deposit("acc-1", new BigDecimal("100.00"));

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("100.00"), savingsAccount.getBalance());
        verify(accountEventProducer).publishMovementRecorded(depositMovement, Account.TYPE_SAVINGS);
    }

    @Test
    void deposit_accountNotFound_returnsEmpty() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<Movement> result = movementService.deposit("nonexistent", new BigDecimal("10.00"));

        assertFalse(result.isPresent());
        verify(movementRepository, never()).save(any());
        verify(accountEventProducer, never()).publishMovementRecorded(any(), anyString());
    }

    @Test
    void deposit_nonPositiveAmount_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> movementService.deposit("acc-1", BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> movementService.deposit("acc-1", new BigDecimal("-10")));
        verify(accountRepository, never()).findById(anyString());
    }

    @Test
    void deposit_savingsExceedsMonthlyLimit_throws() {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(savingsAccount));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-1"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn((long) SavingsAccount.DEFAULT_MONTHLY_MOVEMENT_LIMIT);

        assertThrows(IllegalArgumentException.class,
                () -> movementService.deposit("acc-1", new BigDecimal("100.00")));
        verify(accountRepository, never()).save(any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    void deposit_fixedTermOnAllowedDay_success() {
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003",
                LocalDate.now().getDayOfMonth());
        fixedTerm.setId("acc-3");
        Movement fixedDeposit = new Movement("acc-3", Movement.TYPE_DEPOSIT, new BigDecimal("200.00"));
        when(accountRepository.findById("acc-3")).thenReturn(Optional.of(fixedTerm));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-3"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any(Movement.class))).thenReturn(fixedDeposit);

        Optional<Movement> result = movementService.deposit("acc-3", new BigDecimal("200.00"));

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("200.00"), fixedTerm.getBalance());
    }

    @Test
    void deposit_fixedTermOnWrongDay_throws() {
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003",
                LocalDate.now().plusDays(1).getDayOfMonth());
        fixedTerm.setId("acc-3");
        when(accountRepository.findById("acc-3")).thenReturn(Optional.of(fixedTerm));

        assertThrows(IllegalArgumentException.class,
                () -> movementService.deposit("acc-3", new BigDecimal("200.00")));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void deposit_fixedTermSecondMovementSameMonth_throws() {
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003",
                LocalDate.now().getDayOfMonth());
        fixedTerm.setId("acc-3");
        when(accountRepository.findById("acc-3")).thenReturn(Optional.of(fixedTerm));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-3"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> movementService.deposit("acc-3", new BigDecimal("200.00")));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void withdraw_success() {
        savingsAccount.setBalance(new BigDecimal("500.00"));
        Movement withdrawal = new Movement("acc-1", Movement.TYPE_WITHDRAWAL, new BigDecimal("100.00"));
        withdrawal.setId("mov-2");
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(savingsAccount));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(
                eq("acc-1"), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(0L);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any(Movement.class))).thenReturn(withdrawal);

        Optional<Movement> result = movementService.withdraw("acc-1", new BigDecimal("100.00"));

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("400.00"), savingsAccount.getBalance());
    }

    @Test
    void withdraw_insufficientBalance_throws() {
        savingsAccount.setBalance(new BigDecimal("50.00"));
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(savingsAccount));

        assertThrows(IllegalArgumentException.class,
                () -> movementService.withdraw("acc-1", new BigDecimal("100.00")));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void withdraw_accountNotFound_returnsEmpty() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<Movement> result = movementService.withdraw("nonexistent", new BigDecimal("10.00"));

        assertFalse(result.isPresent());
    }

    @Test
    void findMovements_accountNotFound_returnsEmpty() {
        when(accountRepository.existsById("nonexistent")).thenReturn(false);

        Optional<List<Movement>> result = movementService.findMovements("nonexistent");

        assertFalse(result.isPresent());
        verify(movementRepository, never()).findByAccountIdOrderByOccurredAtDesc(anyString());
    }

    @Test
    void findMovements_accountExists_returnsMovements() {
        when(accountRepository.existsById("acc-1")).thenReturn(true);
        when(movementRepository.findByAccountIdOrderByOccurredAtDesc("acc-1"))
                .thenReturn(List.of(depositMovement));

        Optional<List<Movement>> result = movementService.findMovements("acc-1");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("mov-1", result.get().get(0).getId());
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

        var result = movementService.toMovementResponseList(List.of(depositMovement, second));

        assertEquals(2, result.size());
    }
}
