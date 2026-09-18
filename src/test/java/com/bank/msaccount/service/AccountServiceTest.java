package com.bank.msaccount.service;

import com.bank.msaccount.dto.AccountResponse;
import com.bank.msaccount.event.AccountEventProducer;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.CustomerView;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.Movement;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.repository.AccountRepository;
import com.bank.msaccount.repository.CustomerViewRepository;
import com.bank.msaccount.repository.MovementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AccountService}.
 * Valida las reglas de cardinalidad por tipo de cliente, el CRUD completo
 * y la publicacion de eventos de dominio.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CustomerViewRepository customerViewRepository;

    @Mock
    private AccountEventProducer accountEventProducer;

    @Mock
    private MovementRepository movementRepository;

    @InjectMocks
    private AccountService accountService;

    private CustomerView personalView;
    private CustomerView businessView;
    private CustomerView vipView;
    private CustomerView pymeView;
    private SavingsAccount savingsAccount;
    private CheckingAccount checkingAccount;

    @BeforeEach
    void setUp() {
        personalView = new CustomerView("cust-1", "PERSONAL", "REGULAR", "12345678");
        businessView = new CustomerView("cust-2", "BUSINESS", "REGULAR", "87654321");
        vipView = new CustomerView("cust-3", "PERSONAL", "VIP", "11223344");
        vipView.setHasCreditCard(true);
        pymeView = new CustomerView("cust-5", "BUSINESS", "PYME", "99887766");
        pymeView.setHasCreditCard(true);

        savingsAccount = new SavingsAccount("cust-1", "100000000001");
        savingsAccount.setId("acc-1");

        checkingAccount = new CheckingAccount("cust-2", "100000000002", List.of("cust-2"), List.of());
        checkingAccount.setId("acc-2");

        accountService.setMinimumOpeningAmount(java.math.BigDecimal.ZERO);
        accountService.setVipMinimumDailyAverage(new BigDecimal("1000.00"));
    }

    @Test
    void openSavingsAccount_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_SAVINGS))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(SavingsAccount.class))).thenReturn(Mono.just(savingsAccount));

        StepVerifier.create(accountService.openSavingsAccount("cust-1", null))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(result);
                    assertEquals(Account.TYPE_SAVINGS, result.getAccountType());
                })
                .verifyComplete();

        verify(accountEventProducer).publishAccountOpened(savingsAccount);
    }

    @Test
    void openSavingsAccount_withInitialBalance_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_SAVINGS))
                .thenReturn(Mono.just(0L));
        SavingsAccount accountWithBalance = new SavingsAccount("cust-1", "100000000001");
        accountWithBalance.setId("acc-1");
        accountWithBalance.setBalance(new BigDecimal("100.00"));
        when(accountRepository.save(any(SavingsAccount.class))).thenReturn(Mono.just(accountWithBalance));

        StepVerifier.create(accountService.openSavingsAccount("cust-1", new BigDecimal("100.00")))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(result);
                    assertEquals(new BigDecimal("100.00"), result.getBalance());
                })
                .verifyComplete();
    }

    @Test
    void openSavingsAccount_belowMinimum_throws() {
        accountService.setMinimumOpeningAmount(new BigDecimal("50.00"));

        StepVerifier.create(accountService.validateInitialBalanceForTest(new BigDecimal("10.00")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void openSavingsAccount_customerNotFound_throws() {
        when(customerViewRepository.findById("unknown")).thenReturn(Mono.empty());

        StepVerifier.create(accountService.openSavingsAccount("unknown", null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openSavingsAccount_businessCustomer_throws() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Mono.just(businessView));

        StepVerifier.create(accountService.openSavingsAccount("cust-2", null))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void openSavingsAccount_alreadyHasSavings_throws() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_SAVINGS))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(accountService.openSavingsAccount("cust-1", null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openCheckingAccount_personal_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_CHECKING))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(CheckingAccount.class))).thenReturn(Mono.just(checkingAccount));

        StepVerifier.create(accountService.openCheckingAccount("cust-1", null, null, null))
                .assertNext(org.junit.jupiter.api.Assertions::assertNotNull)
                .verifyComplete();

        verify(accountEventProducer).publishAccountOpened(checkingAccount);
    }

    @Test
    void openCheckingAccount_personalAlreadyHasOne_throws() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_CHECKING))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(accountService.openCheckingAccount("cust-1", null, null, null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openCheckingAccount_business_defaultsHolderToCustomer() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Mono.just(businessView));
        when(accountRepository.save(any(CheckingAccount.class))).thenReturn(Mono.just(checkingAccount));

        StepVerifier.create(accountService.openCheckingAccount("cust-2", null, null, null))
                .assertNext(org.junit.jupiter.api.Assertions::assertNotNull)
                .verifyComplete();

        ArgumentCaptor<CheckingAccount> captor = ArgumentCaptor.forClass(CheckingAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(List.of("cust-2"), captor.getValue().getHolderIds());
        assertEquals(List.of(), captor.getValue().getSignerIds());
    }

    @Test
    void openCheckingAccount_businessWithExplicitHoldersAndSigners_success() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Mono.just(businessView));
        when(accountRepository.save(any(CheckingAccount.class))).thenReturn(Mono.just(checkingAccount));

        StepVerifier.create(accountService.openCheckingAccount("cust-2",
                        List.of("cust-2", "cust-3"), List.of("cust-4"), null))
                .assertNext(org.junit.jupiter.api.Assertions::assertNotNull)
                .verifyComplete();

        ArgumentCaptor<CheckingAccount> captor = ArgumentCaptor.forClass(CheckingAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(List.of("cust-2", "cust-3"), captor.getValue().getHolderIds());
        assertEquals(List.of("cust-4"), captor.getValue().getSignerIds());
    }

    @Test
    void openCheckingAccount_customerNotFound_throws() {
        when(customerViewRepository.findById("unknown")).thenReturn(Mono.empty());

        StepVerifier.create(accountService.openCheckingAccount("unknown", null, null, null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openFixedTermAccount_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003");
        fixedTerm.setId("acc-3");
        when(accountRepository.save(any(FixedTermAccount.class))).thenReturn(Mono.just(fixedTerm));

        StepVerifier.create(accountService.openFixedTermAccount("cust-1", null, null))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(result);
                    assertEquals(Account.TYPE_FIXED_TERM, result.getAccountType());
                })
                .verifyComplete();
    }

    @Test
    void openFixedTermAccount_businessCustomer_throws() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Mono.just(businessView));

        StepVerifier.create(accountService.openFixedTermAccount("cust-2", null, null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openFixedTermAccount_customAllowedDay() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Mono.just(personalView));
        when(accountRepository.save(any(FixedTermAccount.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.openFixedTermAccount("cust-1", 15, null))
                .assertNext(result -> assertEquals(15, result.getAllowedDayOfMonth()))
                .verifyComplete();
    }

    @Test
    void findById_found() {
        when(accountRepository.findById("acc-1")).thenReturn(Mono.just(savingsAccount));

        StepVerifier.create(accountService.findById("acc-1"))
                .assertNext(customer -> assertEquals("acc-1", customer.getId()))
                .verifyComplete();
    }

    @Test
    void findById_notFound() {
        when(accountRepository.findById("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(accountService.findById("nonexistent"))
                .verifyComplete();
    }

    @Test
    void findAll_returnsFlux() {
        when(accountRepository.findAll()).thenReturn(Flux.fromIterable(
                Arrays.asList(savingsAccount, checkingAccount)));

        StepVerifier.create(accountService.findAll())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void update_checkingAccount_updatesHoldersAndSigners() {
        when(accountRepository.findById("acc-2")).thenReturn(Mono.just(checkingAccount));
        when(accountRepository.save(any(CheckingAccount.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.update("acc-2", List.of("cust-2", "cust-3"), List.of("cust-5")))
                .assertNext(result -> {
                    CheckingAccount updated = (CheckingAccount) result;
                    assertEquals(List.of("cust-2", "cust-3"), updated.getHolderIds());
                    assertEquals(List.of("cust-5"), updated.getSignerIds());
                })
                .verifyComplete();
    }

    @Test
    void update_checkingAccount_partialUpdateKeepsHolders() {
        when(accountRepository.findById("acc-2")).thenReturn(Mono.just(checkingAccount));
        when(accountRepository.save(any(CheckingAccount.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.update("acc-2", null, List.of("cust-9")))
                .assertNext(result -> {
                    CheckingAccount updated = (CheckingAccount) result;
                    assertEquals(List.of("cust-2"), updated.getHolderIds());
                    assertEquals(List.of("cust-9"), updated.getSignerIds());
                })
                .verifyComplete();
    }

    @Test
    void update_nonCheckingAccount_throws() {
        when(accountRepository.findById("acc-1")).thenReturn(Mono.just(savingsAccount));

        StepVerifier.create(accountService.update("acc-1", List.of("cust-1"), null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void update_emptyHolders_throws() {
        when(accountRepository.findById("acc-2")).thenReturn(Mono.just(checkingAccount));

        StepVerifier.create(accountService.update("acc-2", List.of(), null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void update_notFound() {
        when(accountRepository.findById("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(accountService.update("nonexistent", List.of("cust-1"), null))
                .verifyComplete();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void delete_found_returnsTrue() {
        when(accountRepository.existsById("acc-1")).thenReturn(Mono.just(true));
        when(accountRepository.deleteById(org.mockito.ArgumentMatchers.<String>any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(accountService.delete("acc-1"))
                .assertNext(org.junit.jupiter.api.Assertions::assertTrue)
                .verifyComplete();
    }

    @Test
    void delete_notFound_returnsFalse() {
        when(accountRepository.existsById("nonexistent")).thenReturn(Mono.just(false));

        StepVerifier.create(accountService.delete("nonexistent"))
                .assertNext(org.junit.jupiter.api.Assertions::assertFalse)
                .verifyComplete();

        verify(accountRepository, never()).deleteById(org.mockito.ArgumentMatchers.<String>any());
    }

    @Test
    void toResponse_savingsAccount() {
        AccountResponse response = accountService.toResponse(savingsAccount);

        assertEquals("acc-1", response.getId());
        assertEquals("100000000001", response.getAccountNumber());
        assertEquals("cust-1", response.getCustomerId());
        assertEquals(Account.TYPE_SAVINGS, response.getAccountType());
        assertNull(response.getHolderIds());
        assertNull(response.getSignerIds());
    }

    @Test
    void toResponse_checkingAccount() {
        AccountResponse response = accountService.toResponse(checkingAccount);

        assertEquals(Account.TYPE_CHECKING, response.getAccountType());
        assertEquals(List.of("cust-2"), response.getHolderIds());
        assertEquals(List.of(), response.getSignerIds());
    }

    @Test
    void toResponseList() {
        Flux<Account> accounts = Flux.fromIterable(Arrays.asList(savingsAccount, checkingAccount));

        StepVerifier.create(accountService.toResponseList(accounts))
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void openSavingsAccount_vipWithCreditCard_success() {
        when(customerViewRepository.findById("cust-3")).thenReturn(Mono.just(vipView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-3", Account.TYPE_SAVINGS))
                .thenReturn(Mono.just(0L));
        SavingsAccount vipAccount = new SavingsAccount("cust-3", "100000000010");
        vipAccount.setId("acc-vip");
        when(accountRepository.save(any(SavingsAccount.class))).thenReturn(Mono.just(vipAccount));

        StepVerifier.create(accountService.openSavingsAccount("cust-3", null))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(result);
                    assertEquals(Account.TYPE_SAVINGS, result.getAccountType());
                })
                .verifyComplete();

        ArgumentCaptor<SavingsAccount> captor = ArgumentCaptor.forClass(SavingsAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(new BigDecimal("1000.00"), captor.getValue().getMinimumDailyAverage());
        verify(accountEventProducer).publishAccountOpened(vipAccount);
    }

    @Test
    void openSavingsAccount_vipWithoutCreditCard_throws() {
        CustomerView vipNoCard = new CustomerView("cust-4", "PERSONAL", "VIP", "55667788");
        vipNoCard.setHasCreditCard(false);
        when(customerViewRepository.findById("cust-4")).thenReturn(Mono.just(vipNoCard));

        StepVerifier.create(accountService.openSavingsAccount("cust-4", null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openSavingsAccount_vipHasCreditCard_setsMinimumDailyAverage() {
        when(customerViewRepository.findById("cust-3")).thenReturn(Mono.just(vipView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-3", Account.TYPE_SAVINGS))
                .thenReturn(Mono.just(0L));
        when(accountRepository.save(any(SavingsAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.openSavingsAccount("cust-3", null))
                .assertNext(result -> {
                    SavingsAccount saved = (SavingsAccount) result;
                    assertEquals(new BigDecimal("1000.00"), saved.getMinimumDailyAverage());
                })
                .verifyComplete();
    }

    @Test
    void toResponse_savingsAccountVip_includesMinimumDailyAverage() {
        SavingsAccount vipSavings = new SavingsAccount("cust-3", "100000000010");
        vipSavings.setId("acc-vip");
        vipSavings.setMinimumDailyAverage(new BigDecimal("1000.00"));

        AccountResponse response = accountService.toResponse(vipSavings);

        assertEquals(new BigDecimal("1000.00"), response.getMinimumDailyAverage());
    }

    @Test
    void openCheckingAccount_pymeWithCreditCard_success() {
        when(customerViewRepository.findById("cust-5")).thenReturn(Mono.just(pymeView));
        when(accountRepository.save(any(CheckingAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.openCheckingAccount("cust-5", null, null, null))
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertNotNull(result);
                    assertEquals(Account.TYPE_CHECKING, result.getAccountType());
                    assertTrue(result.getCommissionFree());
                })
                .verifyComplete();
    }

    @Test
    void openCheckingAccount_pymeWithoutCreditCard_throws() {
        CustomerView pymeNoCard = new CustomerView("cust-6", "BUSINESS", "PYME", "55443322");
        pymeNoCard.setHasCreditCard(false);
        when(customerViewRepository.findById("cust-6")).thenReturn(Mono.just(pymeNoCard));

        StepVerifier.create(accountService.openCheckingAccount("cust-6", null, null, null))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void openCheckingAccount_pyme_setsCommissionFree() {
        when(customerViewRepository.findById("cust-5")).thenReturn(Mono.just(pymeView));
        when(accountRepository.save(any(CheckingAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.openCheckingAccount("cust-5", null, null, null))
                .assertNext(result -> {
                    CheckingAccount saved = (CheckingAccount) result;
                    assertTrue(saved.getCommissionFree());
                })
                .verifyComplete();
    }

    @Test
    void toResponse_checkingAccountPyme_includesCommissionFree() {
        CheckingAccount pymeChecking = new CheckingAccount("cust-5", "100000000020",
                List.of("cust-5"), List.of());
        pymeChecking.setId("acc-pyme");
        pymeChecking.setCommissionFree(true);

        AccountResponse response = accountService.toResponse(pymeChecking);

        assertTrue(response.getCommissionFree());
    }

    @Test
    void openCheckingAccount_regularBusiness_notCommissionFree() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Mono.just(businessView));
        when(accountRepository.save(any(CheckingAccount.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.openCheckingAccount("cust-2", null, null, null))
                .assertNext(result -> {
                    CheckingAccount saved = (CheckingAccount) result;
                    assertFalse(saved.getCommissionFree());
                })
                .verifyComplete();
    }

    @Test
    void transfer_success() {
        SavingsAccount source = new SavingsAccount("cust-1", "100000000001");
        source.setId("acc-src");
        source.setBalance(new BigDecimal("5000.00"));
        SavingsAccount target = new SavingsAccount("cust-1", "100000000002");
        target.setId("acc-tgt");
        target.setBalance(new BigDecimal("1000.00"));

        when(accountRepository.findById("acc-src")).thenReturn(Mono.just(source));
        when(accountRepository.findById("acc-tgt")).thenReturn(Mono.just(target));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(anyString(), any(), any()))
                .thenReturn(Mono.just(0L));
        when(movementRepository.save(any(Movement.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(accountService.transfer("acc-src", "acc-tgt", new BigDecimal("1000.00")))
                .assertNext(result -> {
                    assertEquals("acc-src", result.getId());
                    assertEquals(new BigDecimal("4000.00"), result.getBalance());
                })
                .verifyComplete();

        verify(movementRepository, times(2)).save(any(Movement.class));
        verify(accountEventProducer, times(2)).publishMovementRecorded(any(), anyString());
    }

    @Test
    void transfer_insufficientBalance_throws() {
        SavingsAccount source = new SavingsAccount("cust-1", "100000000001");
        source.setId("acc-src");
        source.setBalance(new BigDecimal("100.00"));
        SavingsAccount target = new SavingsAccount("cust-1", "100000000002");
        target.setId("acc-tgt");
        target.setBalance(new BigDecimal("0"));

        when(accountRepository.findById("acc-src")).thenReturn(Mono.just(source));
        when(accountRepository.findById("acc-tgt")).thenReturn(Mono.just(target));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(anyString(), any(), any()))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(accountService.transfer("acc-src", "acc-tgt", new BigDecimal("500.00")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    void transfer_sameAccount_throws() {
        StepVerifier.create(accountService.transfer("acc-1", "acc-1", new BigDecimal("100.00")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void transfer_invalidAmount_throws() {
        StepVerifier.create(accountService.transfer("acc-1", "acc-2", BigDecimal.ZERO))
                .expectError(IllegalArgumentException.class)
                .verify();

        StepVerifier.create(accountService.transfer("acc-1", "acc-2", new BigDecimal("-100")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void transfer_sourceNotFound_throws() {
        when(accountRepository.findById("nonexistent")).thenAnswer(inv -> Mono.empty());
        when(accountRepository.findById("acc-2")).thenAnswer(inv -> Mono.empty());

        StepVerifier.create(accountService.transfer("nonexistent", "acc-2", new BigDecimal("100.00")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void transfer_targetNotFound_throws() {
        SavingsAccount source = new SavingsAccount("cust-1", "100000000001");
        source.setId("acc-src");
        source.setBalance(new BigDecimal("5000.00"));

        when(accountRepository.findById("acc-src")).thenReturn(Mono.just(source));
        when(accountRepository.findById("nonexistent")).thenAnswer(inv -> Mono.empty());

        StepVerifier.create(accountService.transfer("acc-src", "nonexistent", new BigDecimal("100.00")))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void transfer_savingsAccount_exceedsMonthlyLimit_throws() {
        SavingsAccount source = new SavingsAccount("cust-1", "100000000001");
        source.setId("acc-src");
        source.setBalance(new BigDecimal("5000.00"));
        SavingsAccount target = new SavingsAccount("cust-1", "100000000002");
        target.setId("acc-tgt");

        when(accountRepository.findById("acc-src")).thenReturn(Mono.just(source));
        when(accountRepository.findById("acc-tgt")).thenReturn(Mono.just(target));
        when(movementRepository.countByAccountIdAndOccurredAtBetween(anyString(), any(), any()))
                .thenReturn(Mono.just((long) SavingsAccount.DEFAULT_MONTHLY_MOVEMENT_LIMIT));

        StepVerifier.create(accountService.transfer("acc-src", "acc-tgt", new BigDecimal("100.00")))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(accountRepository, never()).save(any());
    }
}
