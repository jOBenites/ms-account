package com.bank.msaccount.controller;

import com.bank.msaccount.dto.AccountResponse;
import com.bank.msaccount.dto.AccountUpdateRequest;
import com.bank.msaccount.dto.MovementRequest;
import com.bank.msaccount.dto.MovementResponse;
import com.bank.msaccount.dto.OpenAccountRequest;
import com.bank.msaccount.dto.TransferRequest;
import com.bank.msaccount.service.AccountService;
import com.bank.msaccount.service.MovementService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controlador REST reactivo para la gestion de cuentas bancarias.
 * Expone apertura de cuentas (ahorro, corriente, plazo fijo), CRUD completo,
 * registro de depositos y retiros, y consulta de movimientos.
 */
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final MovementService movementService;

    /**
     * Abre una cuenta de ahorro para un cliente personal.
     *
     * @param request solicitud con el customerId del titular y saldo inicial opcional
     * @return Mono con la cuenta creada y codigo 201
     */
    @PostMapping("/savings")
    public Mono<ResponseEntity<AccountResponse>> openSavingsAccount(@RequestBody OpenAccountRequest request) {
        return accountService.openSavingsAccount(request.getCustomerId(), request.getInitialBalance())
                .map(account -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(accountService.toResponse(account)));
    }

    /**
     * Abre una cuenta corriente para un cliente personal o empresarial.
     *
     * @param request solicitud con customerId, titulares, firmantes y saldo inicial opcional
     * @return Mono con la cuenta creada y codigo 201
     */
    @PostMapping("/checking")
    public Mono<ResponseEntity<AccountResponse>> openCheckingAccount(@RequestBody OpenAccountRequest request) {
        return accountService.openCheckingAccount(
                        request.getCustomerId(), request.getHolderIds(), request.getSignerIds(),
                        request.getInitialBalance())
                .map(account -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(accountService.toResponse(account)));
    }

    /**
     * Abre una cuenta a plazo fijo para un cliente personal.
     *
     * @param request solicitud con el customerId del titular, dia permitido y saldo inicial opcional
     * @return Mono con la cuenta creada y codigo 201
     */
    @PostMapping("/fixed-term")
    public Mono<ResponseEntity<AccountResponse>> openFixedTermAccount(@RequestBody OpenAccountRequest request) {
        return accountService.openFixedTermAccount(
                        request.getCustomerId(), request.getAllowedDayOfMonth(), request.getInitialBalance())
                .map(account -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(accountService.toResponse(account)));
    }

    /**
     * Registra un deposito en una cuenta.
     *
     * @param id identificador de la cuenta
     * @param request solicitud con el monto a depositar
     * @return Mono con el movimiento registrado y codigo 201, o 404 si la cuenta no existe
     */
    @PostMapping("/{id}/deposits")
    public Mono<ResponseEntity<MovementResponse>> deposit(
            @PathVariable String id,
            @RequestBody MovementRequest request) {
        return movementService.deposit(id, request.getAmount())
                .map(movement -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(movementService.toMovementResponse(movement)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Registra un retiro de una cuenta.
     *
     * @param id identificador de la cuenta
     * @param request solicitud con el monto a retirar
     * @return Mono con el movimiento registrado y codigo 201, o 404 si la cuenta no existe
     */
    @PostMapping("/{id}/withdrawals")
    public Mono<ResponseEntity<MovementResponse>> withdraw(
            @PathVariable String id,
            @RequestBody MovementRequest request) {
        return movementService.withdraw(id, request.getAmount())
                .map(movement -> ResponseEntity.status(HttpStatus.CREATED)
                        .body(movementService.toMovementResponse(movement)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Lista los movimientos de una cuenta del mas reciente al mas antiguo.
     *
     * @param id identificador de la cuenta
     * @return Flux con los movimientos, o 404 si la cuenta no existe
     */
    @GetMapping("/{id}/movements")
    public Mono<ResponseEntity<Flux<MovementResponse>>> getMovements(@PathVariable String id) {
        return accountService.findById(id)
                .map(account -> ResponseEntity.ok(
                        movementService.toMovementResponseList(
                                movementService.findMovements(id))))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Obtiene una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return Mono con la cuenta encontrada o 404 si no existe
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<AccountResponse>> getAccountById(@PathVariable String id) {
        return accountService.findById(id)
                .map(account -> ResponseEntity.ok(accountService.toResponse(account)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Lista todas las cuentas registradas.
     *
     * @return Flux con las cuentas
     */
    @GetMapping
    public Flux<AccountResponse> getAllAccounts() {
        return accountService.toResponseList(accountService.findAll());
    }

    /**
     * Actualiza los titulares y firmantes de una cuenta corriente.
     *
     * @param id identificador de la cuenta
     * @param request campos a actualizar (holderIds, signerIds)
     * @return Mono con la cuenta actualizada o 404 si no existe
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<AccountResponse>> updateAccount(
            @PathVariable String id,
            @RequestBody AccountUpdateRequest request) {
        return accountService.update(id, request.getHolderIds(), request.getSignerIds())
                .map(account -> ResponseEntity.ok(accountService.toResponse(account)))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Elimina una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return Mono con 204 si se elimino, 404 si no existe
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAccount(@PathVariable String id) {
        return accountService.delete(id)
                .flatMap(deleted -> {
                    if (deleted) {
                        return Mono.just(ResponseEntity.noContent().<Void>build());
                    }
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }

    /**
     * Transfiere fondos entre dos cuentas del mismo banco.
     *
     * @param request solicitud con cuentas origen, destino y monto
     * @return Mono con la cuenta origen actualizada y codigo 200
     */
    @PostMapping("/transfers")
    public Mono<ResponseEntity<AccountResponse>> transfer(@RequestBody TransferRequest request) {
        return accountService.transfer(
                        request.getSourceAccountId(), request.getTargetAccountId(), request.getAmount())
                .map(account -> ResponseEntity.ok(accountService.toResponse(account)));
    }
}
