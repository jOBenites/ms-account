package com.bank.msaccount.controller;

import com.bank.msaccount.dto.AccountResponse;
import com.bank.msaccount.dto.AccountUpdateRequest;
import com.bank.msaccount.dto.OpenAccountRequest;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AccountController}.
 * Valida los endpoints REST y los codigos de respuesta HTTP.
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AccountController accountController;

    private SavingsAccount savingsAccount;
    private CheckingAccount checkingAccount;
    private AccountResponse savingsResponse;
    private AccountResponse checkingResponse;

    @BeforeEach
    void setUp() {
        savingsAccount = new SavingsAccount("cust-1", "100000000001");
        savingsAccount.setId("acc-1");

        checkingAccount = new CheckingAccount("cust-2", "100000000002", List.of("cust-2"), List.of());
        checkingAccount.setId("acc-2");

        savingsResponse = new AccountResponse();
        savingsResponse.setId("acc-1");
        savingsResponse.setAccountNumber("100000000001");
        savingsResponse.setCustomerId("cust-1");
        savingsResponse.setAccountType(Account.TYPE_SAVINGS);

        checkingResponse = new AccountResponse();
        checkingResponse.setId("acc-2");
        checkingResponse.setAccountNumber("100000000002");
        checkingResponse.setCustomerId("cust-2");
        checkingResponse.setAccountType(Account.TYPE_CHECKING);
        checkingResponse.setHolderIds(List.of("cust-2"));
        checkingResponse.setSignerIds(List.of());
    }

    @Test
    void openSavingsAccount_returns201() {
        when(accountService.openSavingsAccount("cust-1")).thenReturn(savingsAccount);
        when(accountService.toResponse(savingsAccount)).thenReturn(savingsResponse);

        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("cust-1");
        ResponseEntity<AccountResponse> response = accountController.openSavingsAccount(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("acc-1", response.getBody().getId());
    }

    @Test
    void openCheckingAccount_returns201() {
        when(accountService.openCheckingAccount("cust-2", List.of("cust-2"), List.of()))
                .thenReturn(checkingAccount);
        when(accountService.toResponse(checkingAccount)).thenReturn(checkingResponse);

        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("cust-2");
        request.setHolderIds(List.of("cust-2"));
        request.setSignerIds(List.of());
        ResponseEntity<AccountResponse> response = accountController.openCheckingAccount(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(Account.TYPE_CHECKING, response.getBody().getAccountType());
    }

    @Test
    void openFixedTermAccount_returns201() {
        FixedTermAccount fixedTermAccount = new FixedTermAccount("cust-1", "100000000003");
        when(accountService.openFixedTermAccount("cust-1")).thenReturn(fixedTermAccount);
        AccountResponse fixedResponse = new AccountResponse();
        fixedResponse.setAccountType(Account.TYPE_FIXED_TERM);
        when(accountService.toResponse(any(FixedTermAccount.class))).thenReturn(fixedResponse);

        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("cust-1");
        ResponseEntity<AccountResponse> response = accountController.openFixedTermAccount(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(Account.TYPE_FIXED_TERM, response.getBody().getAccountType());
    }

    @Test
    void getAccountById_found() {
        when(accountService.findById("acc-1")).thenReturn(Optional.of(savingsAccount));
        when(accountService.toResponse(savingsAccount)).thenReturn(savingsResponse);

        ResponseEntity<AccountResponse> response = accountController.getAccountById("acc-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("acc-1", response.getBody().getId());
    }

    @Test
    void getAccountById_notFound() {
        when(accountService.findById("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<AccountResponse> response = accountController.getAccountById("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void getAllAccounts_returnsList() {
        List<Account> accounts = Arrays.asList(savingsAccount, checkingAccount);
        when(accountService.findAll()).thenReturn(accounts);
        when(accountService.toResponseList(accounts))
                .thenReturn(Arrays.asList(savingsResponse, checkingResponse));

        ResponseEntity<List<AccountResponse>> response = accountController.getAllAccounts();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void updateAccount_found() {
        when(accountService.update("acc-2", List.of("cust-2", "cust-3"), null))
                .thenReturn(Optional.of(checkingAccount));
        when(accountService.toResponse(checkingAccount)).thenReturn(checkingResponse);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setHolderIds(List.of("cust-2", "cust-3"));
        ResponseEntity<AccountResponse> response = accountController.updateAccount("acc-2", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void updateAccount_notFound() {
        when(accountService.update("nonexistent", null, null)).thenReturn(Optional.empty());

        AccountUpdateRequest request = new AccountUpdateRequest();
        ResponseEntity<AccountResponse> response = accountController.updateAccount("nonexistent", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void deleteAccount_found() {
        when(accountService.delete("acc-1")).thenReturn(true);

        ResponseEntity<Void> response = accountController.deleteAccount("acc-1");

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void deleteAccount_notFound() {
        when(accountService.delete("nonexistent")).thenReturn(false);

        ResponseEntity<Void> response = accountController.deleteAccount("nonexistent");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
