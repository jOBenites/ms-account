package com.bank.msaccount.service;

import com.bank.msaccount.dto.AccountResponse;
import com.bank.msaccount.event.AccountEventProducer;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.CustomerView;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.repository.AccountRepository;
import com.bank.msaccount.repository.CustomerViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private AccountService accountService;

    private CustomerView personalView;
    private CustomerView businessView;
    private SavingsAccount savingsAccount;
    private CheckingAccount checkingAccount;

    @BeforeEach
    void setUp() {
        personalView = new CustomerView("cust-1", "PERSONAL", "REGULAR", "12345678");
        businessView = new CustomerView("cust-2", "BUSINESS", "REGULAR", "87654321");

        savingsAccount = new SavingsAccount("cust-1", "100000000001");
        savingsAccount.setId("acc-1");

        checkingAccount = new CheckingAccount("cust-2", "100000000002", List.of("cust-2"), List.of());
        checkingAccount.setId("acc-2");
    }

    @Test
    void openSavingsAccount_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.of(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_SAVINGS)).thenReturn(0L);
        when(accountRepository.save(any(SavingsAccount.class))).thenReturn(savingsAccount);

        SavingsAccount result = accountService.openSavingsAccount("cust-1");

        assertNotNull(result);
        assertEquals(Account.TYPE_SAVINGS, result.getAccountType());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        verify(accountEventProducer).publishAccountOpened(savingsAccount);
    }

    @Test
    void openSavingsAccount_customerNotFound_throws() {
        when(customerViewRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> accountService.openSavingsAccount("unknown"));
        verify(accountRepository, never()).save(any());
        verify(accountEventProducer, never()).publishAccountOpened(any());
    }

    @Test
    void openSavingsAccount_businessCustomer_throws() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Optional.of(businessView));

        assertThrows(IllegalArgumentException.class, () -> accountService.openSavingsAccount("cust-2"));
        verify(accountRepository, never()).countByCustomerIdAndAccountType(anyString(), anyString());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void openSavingsAccount_alreadyHasSavings_throws() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.of(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_SAVINGS)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class, () -> accountService.openSavingsAccount("cust-1"));
        verify(accountRepository, never()).save(any());
        verify(accountEventProducer, never()).publishAccountOpened(any());
    }

    @Test
    void openCheckingAccount_personal_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.of(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_CHECKING)).thenReturn(0L);
        when(accountRepository.save(any(CheckingAccount.class))).thenReturn(checkingAccount);

        CheckingAccount result = accountService.openCheckingAccount("cust-1", null, null);

        assertNotNull(result);
        verify(accountEventProducer).publishAccountOpened(checkingAccount);
    }

    @Test
    void openCheckingAccount_personalAlreadyHasOne_throws() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.of(personalView));
        when(accountRepository.countByCustomerIdAndAccountType("cust-1", Account.TYPE_CHECKING)).thenReturn(1L);

        assertThrows(IllegalArgumentException.class,
                () -> accountService.openCheckingAccount("cust-1", null, null));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void openCheckingAccount_business_defaultsHolderToCustomer() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Optional.of(businessView));
        when(accountRepository.save(any(CheckingAccount.class))).thenReturn(checkingAccount);

        accountService.openCheckingAccount("cust-2", null, null);

        verify(accountRepository, never()).countByCustomerIdAndAccountType(anyString(), anyString());
        ArgumentCaptor<CheckingAccount> captor = ArgumentCaptor.forClass(CheckingAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(List.of("cust-2"), captor.getValue().getHolderIds());
        assertEquals(List.of(), captor.getValue().getSignerIds());
    }

    @Test
    void openCheckingAccount_businessWithExplicitHoldersAndSigners_success() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Optional.of(businessView));
        when(accountRepository.save(any(CheckingAccount.class))).thenReturn(checkingAccount);

        accountService.openCheckingAccount("cust-2", List.of("cust-2", "cust-3"), List.of("cust-4"));

        ArgumentCaptor<CheckingAccount> captor = ArgumentCaptor.forClass(CheckingAccount.class);
        verify(accountRepository).save(captor.capture());
        assertEquals(List.of("cust-2", "cust-3"), captor.getValue().getHolderIds());
        assertEquals(List.of("cust-4"), captor.getValue().getSignerIds());
    }

    @Test
    void openCheckingAccount_customerNotFound_throws() {
        when(customerViewRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> accountService.openCheckingAccount("unknown", null, null));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void openFixedTermAccount_success() {
        when(customerViewRepository.findById("cust-1")).thenReturn(Optional.of(personalView));
        FixedTermAccount fixedTerm = new FixedTermAccount("cust-1", "100000000003");
        fixedTerm.setId("acc-3");
        when(accountRepository.save(any(FixedTermAccount.class))).thenReturn(fixedTerm);

        FixedTermAccount result = accountService.openFixedTermAccount("cust-1");

        assertNotNull(result);
        assertEquals(Account.TYPE_FIXED_TERM, result.getAccountType());
        verify(accountRepository, never()).countByCustomerIdAndAccountType(anyString(), anyString());
        verify(accountEventProducer).publishAccountOpened(fixedTerm);
    }

    @Test
    void openFixedTermAccount_businessCustomer_throws() {
        when(customerViewRepository.findById("cust-2")).thenReturn(Optional.of(businessView));

        assertThrows(IllegalArgumentException.class, () -> accountService.openFixedTermAccount("cust-2"));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void findById_found() {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(savingsAccount));

        Optional<Account> result = accountService.findById("acc-1");

        assertTrue(result.isPresent());
        assertEquals("acc-1", result.get().getId());
    }

    @Test
    void findById_notFound() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<Account> result = accountService.findById("nonexistent");

        assertFalse(result.isPresent());
    }

    @Test
    void findAll_returnsList() {
        when(accountRepository.findAll()).thenReturn(Arrays.asList(savingsAccount, checkingAccount));

        List<Account> result = accountService.findAll();

        assertEquals(2, result.size());
    }

    @Test
    void update_checkingAccount_updatesHoldersAndSigners() {
        when(accountRepository.findById("acc-2")).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.save(any(CheckingAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Account> result = accountService.update("acc-2", List.of("cust-2", "cust-3"), List.of("cust-5"));

        assertTrue(result.isPresent());
        CheckingAccount updated = (CheckingAccount) result.get();
        assertEquals(List.of("cust-2", "cust-3"), updated.getHolderIds());
        assertEquals(List.of("cust-5"), updated.getSignerIds());
    }

    @Test
    void update_checkingAccount_partialUpdateKeepsHolders() {
        when(accountRepository.findById("acc-2")).thenReturn(Optional.of(checkingAccount));
        when(accountRepository.save(any(CheckingAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Account> result = accountService.update("acc-2", null, List.of("cust-9"));

        assertTrue(result.isPresent());
        CheckingAccount updated = (CheckingAccount) result.get();
        assertEquals(List.of("cust-2"), updated.getHolderIds());
        assertEquals(List.of("cust-9"), updated.getSignerIds());
    }

    @Test
    void update_nonCheckingAccount_throws() {
        when(accountRepository.findById("acc-1")).thenReturn(Optional.of(savingsAccount));

        assertThrows(IllegalArgumentException.class,
                () -> accountService.update("acc-1", List.of("cust-1"), null));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void update_emptyHolders_throws() {
        when(accountRepository.findById("acc-2")).thenReturn(Optional.of(checkingAccount));

        assertThrows(IllegalArgumentException.class,
                () -> accountService.update("acc-2", List.of(), null));
        verify(accountRepository, never()).save(any());
    }

    @Test
    void update_notFound() {
        when(accountRepository.findById("nonexistent")).thenReturn(Optional.empty());

        Optional<Account> result = accountService.update("nonexistent", List.of("cust-1"), null);

        assertFalse(result.isPresent());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void delete_found_returnsTrue() {
        when(accountRepository.existsById("acc-1")).thenReturn(true);

        boolean result = accountService.delete("acc-1");

        assertTrue(result);
        verify(accountRepository).deleteById("acc-1");
    }

    @Test
    void delete_notFound_returnsFalse() {
        when(accountRepository.existsById("nonexistent")).thenReturn(false);

        boolean result = accountService.delete("nonexistent");

        assertFalse(result);
        verify(accountRepository, never()).deleteById(anyString());
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
        List<AccountResponse> responses = accountService.toResponseList(
                Arrays.asList(savingsAccount, checkingAccount));

        assertEquals(2, responses.size());
    }
}
