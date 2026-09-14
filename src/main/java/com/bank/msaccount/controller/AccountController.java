package com.bank.msaccount.controller;

import com.bank.msaccount.dto.AccountResponse;
import com.bank.msaccount.dto.AccountUpdateRequest;
import com.bank.msaccount.dto.OpenAccountRequest;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para la gestion de cuentas bancarias.
 * Expone apertura de cuentas (ahorro, corriente, plazo fijo) y CRUD completo.
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * Abre una cuenta de ahorro para un cliente personal.
     *
     * @param request solicitud con el customerId del titular
     * @return la cuenta creada con codigo 201
     */
    @PostMapping("/savings")
    public ResponseEntity<AccountResponse> openSavingsAccount(@RequestBody OpenAccountRequest request) {
        SavingsAccount account = accountService.openSavingsAccount(request.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.toResponse(account));
    }

    /**
     * Abre una cuenta corriente para un cliente personal o empresarial.
     *
     * @param request solicitud con customerId, titulares y firmantes (opcionales)
     * @return la cuenta creada con codigo 201
     */
    @PostMapping("/checking")
    public ResponseEntity<AccountResponse> openCheckingAccount(@RequestBody OpenAccountRequest request) {
        CheckingAccount account = accountService.openCheckingAccount(
                request.getCustomerId(), request.getHolderIds(), request.getSignerIds());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.toResponse(account));
    }

    /**
     * Abre una cuenta a plazo fijo para un cliente personal.
     *
     * @param request solicitud con el customerId del titular
     * @return la cuenta creada con codigo 201
     */
    @PostMapping("/fixed-term")
    public ResponseEntity<AccountResponse> openFixedTermAccount(@RequestBody OpenAccountRequest request) {
        FixedTermAccount account = accountService.openFixedTermAccount(request.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.toResponse(account));
    }

    /**
     * Obtiene una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return la cuenta encontrada o 404 si no existe
     */
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable String id) {
        return accountService.findById(id)
                .map(account -> ResponseEntity.ok(accountService.toResponse(account)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista todas las cuentas registradas.
     *
     * @return lista de cuentas
     */
    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.toResponseList(accountService.findAll()));
    }

    /**
     * Actualiza los titulares y firmantes de una cuenta corriente.
     *
     * @param id identificador de la cuenta
     * @param request campos a actualizar (holderIds, signerIds)
     * @return la cuenta actualizada o 404 si no existe
     */
    @PutMapping("/{id}")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable String id,
            @RequestBody AccountUpdateRequest request) {
        return accountService.update(id, request.getHolderIds(), request.getSignerIds())
                .map(account -> ResponseEntity.ok(accountService.toResponse(account)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Elimina una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return 204 si se elimino, 404 si no existe
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable String id) {
        if (accountService.delete(id)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
