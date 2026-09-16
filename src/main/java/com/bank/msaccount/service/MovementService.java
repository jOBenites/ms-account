package com.bank.msaccount.service;

import com.bank.msaccount.dto.MovementResponse;
import com.bank.msaccount.event.AccountEventProducer;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.Movement;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.repository.AccountRepository;
import com.bank.msaccount.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;

/**
 * Servicio reactivo de registro de movimientos sobre cuentas bancarias.
 * Aplica las reglas de negocio por tipo de cuenta:
 * ahorro (tope duro de movimientos mensuales) y plazo fijo (un solo
 * movimiento al mes, solo en el dia del mes configurado).
 * Cada movimiento registrado se publica como evento bank.movement.recorded.
 */
@Service
@RequiredArgsConstructor
public class MovementService {

    private final AccountRepository accountRepository;
    private final MovementRepository movementRepository;
    private final AccountEventProducer accountEventProducer;

    /**
     * Registra un deposito en una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @param amount monto a depositar (mayor a cero)
     * @return Mono con el movimiento registrado, o vacio si la cuenta no existe
     */
    public Mono<Movement> deposit(String accountId, BigDecimal amount) {
        return validatePositiveAmount(amount)
                .then(Mono.defer(() -> accountRepository.findById(accountId)))
                .flatMap(account -> {
                    applyMovementRules(account);
                    account.setBalance(account.getBalance().add(amount));
                    return recordMovement(account, Movement.TYPE_DEPOSIT, amount);
                });
    }

    /**
     * Registra un retiro de una cuenta.
     *
     * @param accountId identificador de la cuenta
     * @param amount monto a retirar (mayor a cero)
     * @return Mono con el movimiento registrado, o vacio si la cuenta no existe
     */
    public Mono<Movement> withdraw(String accountId, BigDecimal amount) {
        return validatePositiveAmount(amount)
                .then(Mono.defer(() -> accountRepository.findById(accountId)))
                .flatMap(account -> {
                    if (account.getBalance().compareTo(amount) < 0) {
                        return Mono.error(new IllegalArgumentException("Saldo insuficiente para el retiro"));
                    }
                    applyMovementRules(account);
                    account.setBalance(account.getBalance().subtract(amount));
                    return recordMovement(account, Movement.TYPE_WITHDRAWAL, amount);
                });
    }

    /**
     * Lista los movimientos de una cuenta del mas reciente al mas antiguo.
     *
     * @param accountId identificador de la cuenta
     * @return Flux con los movimientos, o vacio si la cuenta no existe
     */
    public Flux<Movement> findMovements(String accountId) {
        return accountRepository.existsById(accountId)
                .flatMapMany(exists -> {
                    if (!exists) {
                        return Flux.empty();
                    }
                    return movementRepository.findByAccountIdOrderByOccurredAtDesc(accountId);
                });
    }

    /**
     * Convierte una entidad Movement a su DTO de respuesta.
     *
     * @param movement entidad a convertir
     * @return DTO con los campos poblados
     */
    public MovementResponse toMovementResponse(Movement movement) {
        MovementResponse response = new MovementResponse();
        response.setId(movement.getId());
        response.setAccountId(movement.getAccountId());
        response.setMovementType(movement.getMovementType());
        response.setAmount(movement.getAmount());
        response.setOccurredAt(movement.getOccurredAt());
        return response;
    }

    /**
     * Convierte un Flux de entidades Movement a DTOs de respuesta.
     *
     * @param movements Flux de entidades
     * @return Flux de DTOs
     */
    public Flux<MovementResponse> toMovementResponseList(Flux<Movement> movements) {
        return movements.map(this::toMovementResponse);
    }

    private Mono<Void> validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return Mono.error(new IllegalArgumentException("El monto debe ser mayor a cero"));
        }
        return Mono.empty();
    }

    private void applyMovementRules(Account account) {
        if (account instanceof SavingsAccount savings) {
            long currentCount = countCurrentMonthMovements(account.getId()).block();
            if (currentCount >= savings.getMonthlyMovementLimit()) {
                throw new IllegalArgumentException(
                        "Se alcanzo el limite de movimientos mensuales de la cuenta de ahorro");
            }
        }
        if (account instanceof FixedTermAccount fixedTerm) {
            if (LocalDate.now().getDayOfMonth() != fixedTerm.getAllowedDayOfMonth()) {
                throw new IllegalArgumentException("La cuenta a plazo fijo solo permite movimientos el dia "
                        + fixedTerm.getAllowedDayOfMonth() + " de cada mes");
            }
            long currentCount = countCurrentMonthMovements(account.getId()).block();
            if (currentCount > 0) {
                throw new IllegalArgumentException(
                        "La cuenta a plazo fijo permite un solo movimiento por mes");
            }
        }
    }

    private Mono<Long> countCurrentMonthMovements(String accountId) {
        YearMonth current = YearMonth.now();
        return movementRepository.countByAccountIdAndOccurredAtBetween(
                accountId,
                current.atDay(1).atStartOfDay(),
                current.atEndOfMonth().atTime(LocalTime.MAX));
    }

    private Mono<Movement> recordMovement(Account account, String movementType, BigDecimal amount) {
        return accountRepository.save(account)
                .then(movementRepository.save(new Movement(account.getId(), movementType, amount)))
                .doOnNext(movement -> accountEventProducer.publishMovementRecorded(movement, account.getAccountType()));
    }
}
