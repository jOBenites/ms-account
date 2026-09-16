package com.bank.msaccount.controller;

import com.bank.msaccount.dto.AccountResponse;
import com.bank.msaccount.dto.AccountUpdateRequest;
import com.bank.msaccount.dto.MovementRequest;
import com.bank.msaccount.dto.MovementResponse;
import com.bank.msaccount.dto.OpenAccountRequest;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.Movement;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.service.AccountService;
import com.bank.msaccount.service.MovementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AccountController}.
 * Valida los endpoints REST reactivos y los codigos de respuesta HTTP.
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private MovementService movementService;

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
        when(accountService.openSavingsAccount("cust-1")).thenReturn(Mono.just(savingsAccount));
        when(accountService.toResponse(savingsAccount)).thenReturn(savingsResponse);

        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("cust-1");

        StepVerifier.create(accountController.openSavingsAccount(request))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
                    org.junit.jupiter.api.Assertions.assertEquals("acc-1", response.getBody().getId());
                })
                .verifyComplete();
    }

    @Test
    void openCheckingAccount_returns201() {
        when(accountService.openCheckingAccount("cust-2", List.of("cust-2"), List.of()))
                .thenReturn(Mono.just(checkingAccount));
        when(accountService.toResponse(checkingAccount)).thenReturn(checkingResponse);

        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("cust-2");
        request.setHolderIds(List.of("cust-2"));
        request.setSignerIds(List.of());

        StepVerifier.create(accountController.openCheckingAccount(request))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertEquals(Account.TYPE_CHECKING,
                            response.getBody().getAccountType());
                })
                .verifyComplete();
    }

    @Test
    void openFixedTermAccount_returns201() {
        FixedTermAccount fixedTermAccount = new FixedTermAccount("cust-1", "100000000003");
        when(accountService.openFixedTermAccount("cust-1", null)).thenReturn(Mono.just(fixedTermAccount));
        AccountResponse fixedResponse = new AccountResponse();
        fixedResponse.setAccountType(Account.TYPE_FIXED_TERM);
        when(accountService.toResponse(any(FixedTermAccount.class))).thenReturn(fixedResponse);

        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("cust-1");

        StepVerifier.create(accountController.openFixedTermAccount(request))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertEquals(Account.TYPE_FIXED_TERM,
                            response.getBody().getAccountType());
                })
                .verifyComplete();
    }

    @Test
    void getAccountById_found() {
        when(accountService.findById("acc-1")).thenReturn(Mono.just(savingsAccount));
        when(accountService.toResponse(savingsAccount)).thenReturn(savingsResponse);

        StepVerifier.create(accountController.getAccountById("acc-1"))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertEquals("acc-1", response.getBody().getId());
                })
                .verifyComplete();
    }

    @Test
    void getAccountById_notFound() {
        when(accountService.findById("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(accountController.getAccountById("nonexistent"))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void getAllAccounts_returnsFlux() {
        when(accountService.findAll()).thenReturn(Flux.fromIterable(
                Arrays.asList(savingsAccount, checkingAccount)));
        when(accountService.toResponseList(any()))
                .thenReturn(Flux.fromIterable(Arrays.asList(savingsResponse, checkingResponse)));

        StepVerifier.create(accountController.getAllAccounts())
                .expectNextCount(2)
                .verifyComplete();
    }

    @Test
    void updateAccount_found() {
        when(accountService.update("acc-2", List.of("cust-2", "cust-3"), null))
                .thenReturn(Mono.just(checkingAccount));
        when(accountService.toResponse(checkingAccount)).thenReturn(checkingResponse);

        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setHolderIds(List.of("cust-2", "cust-3"));

        StepVerifier.create(accountController.updateAccount("acc-2", request))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.OK, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void updateAccount_notFound() {
        when(accountService.update("nonexistent", null, null)).thenReturn(Mono.empty());

        AccountUpdateRequest request = new AccountUpdateRequest();

        StepVerifier.create(accountController.updateAccount("nonexistent", request))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void deleteAccount_found() {
        when(accountService.delete("acc-1")).thenReturn(Mono.just(true));

        StepVerifier.create(accountController.deleteAccount("acc-1"))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void deleteAccount_notFound() {
        when(accountService.delete("nonexistent")).thenReturn(Mono.just(false));

        StepVerifier.create(accountController.deleteAccount("nonexistent"))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void deposit_returns201() {
        Movement movement = new Movement("acc-1", Movement.TYPE_DEPOSIT, BigDecimal.TEN);
        movement.setId("mov-1");
        when(movementService.deposit("acc-1", BigDecimal.TEN)).thenReturn(Mono.just(movement));
        MovementResponse movementResponse = new MovementResponse();
        movementResponse.setId("mov-1");
        when(movementService.toMovementResponse(movement)).thenReturn(movementResponse);

        MovementRequest request = new MovementRequest();
        request.setAmount(BigDecimal.TEN);

        StepVerifier.create(accountController.deposit("acc-1", request))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertEquals("mov-1", response.getBody().getId());
                })
                .verifyComplete();
    }

    @Test
    void deposit_accountNotFound_returns404() {
        when(movementService.deposit("nonexistent", BigDecimal.TEN)).thenReturn(Mono.empty());

        MovementRequest request = new MovementRequest();
        request.setAmount(BigDecimal.TEN);

        StepVerifier.create(accountController.deposit("nonexistent", request))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void withdraw_returns201() {
        Movement movement = new Movement("acc-1", Movement.TYPE_WITHDRAWAL, BigDecimal.ONE);
        movement.setId("mov-2");
        when(movementService.withdraw("acc-1", BigDecimal.ONE)).thenReturn(Mono.just(movement));
        MovementResponse movementResponse = new MovementResponse();
        when(movementService.toMovementResponse(movement)).thenReturn(movementResponse);

        MovementRequest request = new MovementRequest();
        request.setAmount(BigDecimal.ONE);

        StepVerifier.create(accountController.withdraw("acc-1", request))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.CREATED, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    void getMovements_returns200() {
        Movement movement = new Movement("acc-1", Movement.TYPE_DEPOSIT, BigDecimal.TEN);
        when(movementService.findMovements("acc-1")).thenReturn(Flux.just(movement));
        when(accountService.findById("acc-1")).thenReturn(Mono.just(savingsAccount));
        when(movementService.toMovementResponseList(any()))
                .thenReturn(Flux.just(new MovementResponse()));

        StepVerifier.create(accountController.getMovements("acc-1"))
                .assertNext(response -> {
                    org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
                    org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
                })
                .verifyComplete();
    }

    @Test
    void getMovements_accountNotFound_returns404() {
        when(accountService.findById("nonexistent")).thenReturn(Mono.empty());

        StepVerifier.create(accountController.getMovements("nonexistent"))
                .assertNext(response -> org.junit.jupiter.api.Assertions.assertEquals(
                        HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }
}
