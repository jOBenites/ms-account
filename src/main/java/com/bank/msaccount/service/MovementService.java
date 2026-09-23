package com.bank.msaccount.service;

import com.bank.msaccount.dto.MovementResponse;
import com.bank.msaccount.event.AccountEventProducer;
import com.bank.msaccount.model.Account;
import com.bank.msaccount.model.CheckingAccount;
import com.bank.msaccount.model.FixedTermAccount;
import com.bank.msaccount.model.Movement;
import com.bank.msaccount.model.SavingsAccount;
import com.bank.msaccount.repository.AccountRepository;
import com.bank.msaccount.repository.MovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${account.free-monthly-transactions:5}")
    private int freeMonthlyTransactions;

    @Value("${account.transaction-commission:0.50}")
    private BigDecimal transactionCommission;

    /**
     * Establece el numero de transacciones gratuitas mensuales.
     *
     * @param freeMonthlyTransactions nuevo limite
     */
    public void setFreeMonthlyTransactions(int freeMonthlyTransactions) {
        this.freeMonthlyTransactions = freeMonthlyTransactions;
    }

    /**
     * Establece la comision por transaccion que excede el limite.
     *
     * @param transactionCommission nueva comision
     */
    public void setTransactionCommission(BigDecimal transactionCommission) {
        this.transactionCommission = transactionCommission;
    }

    /**
     * Registra un deposito en una cuenta.
     * Si la cuenta excede el limite de transacciones gratuitas, se cobra comision.
     *
     * @param accountId identificador de la cuenta
     * @param amount monto a depositar (mayor a cero)
     * @return Mono con el movimiento registrado, o vacio si la cuenta no existe
     */
    public Mono<Movement> deposit(String accountId, BigDecimal amount) {
        return validatePositiveAmount(amount)
                .then(Mono.defer(() -> accountRepository.findById(accountId)))
                .flatMap(account -> applyMovementRules(account)
                        .then(Mono.defer(() -> countCurrentMonthMovements(accountId)))
                        .flatMap(currentCount -> {
                            BigDecimal commission = calculateCommission(account, currentCount);
                            account.setBalance(account.getBalance().add(amount).subtract(commission));
                            return recordMovement(account, Movement.TYPE_DEPOSIT, amount, commission);
                        }));
    }

    /**
     * Registra un retiro de una cuenta.
     * Si la cuenta excede el limite de transacciones gratuitas, se cobra comision.
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
                    return applyMovementRules(account)
                            .then(Mono.defer(() -> countCurrentMonthMovements(accountId)))
                            .flatMap(currentCount -> {
                                BigDecimal commission = calculateCommission(account, currentCount);
                                account.setBalance(account.getBalance().subtract(amount).subtract(commission));
                                return recordMovement(account, Movement.TYPE_WITHDRAWAL, amount, commission);
                            });
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
        response.setCommission(movement.getCommission());
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

    private Mono<Void> applyMovementRules(Account account) {
        if (account instanceof SavingsAccount savings) {
            return countCurrentMonthMovements(account.getId())
                    .flatMap(currentCount -> {
                        if (currentCount >= savings.getMonthlyMovementLimit()) {
                            return Mono.error(new IllegalArgumentException(
                                    "Se alcanzo el limite de movimientos mensuales de la cuenta de ahorro"));
                        }
                        return Mono.empty();
                    });
        }
        if (account instanceof FixedTermAccount fixedTerm) {
            if (LocalDate.now().getDayOfMonth() != fixedTerm.getAllowedDayOfMonth()) {
                return Mono.error(new IllegalArgumentException("La cuenta a plazo fijo solo permite movimientos el dia "
                        + fixedTerm.getAllowedDayOfMonth() + " de cada mes"));
            }
            return countCurrentMonthMovements(account.getId())
                    .flatMap(currentCount -> {
                        if (currentCount > 0) {
                            return Mono.error(new IllegalArgumentException(
                                    "La cuenta a plazo fijo permite un solo movimiento por mes"));
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }

    private Mono<Long> countCurrentMonthMovements(String accountId) {
        YearMonth current = YearMonth.now();
        return movementRepository.countByAccountIdAndOccurredAtBetween(
                accountId,
                current.atDay(1).atStartOfDay(),
                current.atEndOfMonth().atTime(LocalTime.MAX));
    }

    private BigDecimal calculateCommission(Account account, long currentCount) {
        if (account instanceof CheckingAccount checking) {
            if (Boolean.TRUE.equals(checking.getCommissionFree())) {
                return BigDecimal.ZERO;
            }
        }
        if (account instanceof SavingsAccount) {
            if (currentCount >= freeMonthlyTransactions) {
                return transactionCommission;
            }
        }
        return BigDecimal.ZERO;
    }

    private Mono<Movement> recordMovement(Account account, String movementType,
                                            BigDecimal amount, BigDecimal commission) {
        return accountRepository.save(account)
                .then(movementRepository.save(new Movement(account.getId(), movementType, amount, commission)))
                .doOnNext(movement -> accountEventProducer.publishMovementRecorded(movement, account.getAccountType()));
    }
}
