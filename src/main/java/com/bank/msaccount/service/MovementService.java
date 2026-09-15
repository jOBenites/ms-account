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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio de registro de movimientos sobre cuentas bancarias.
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
     * @return el movimiento registrado, o empty si la cuenta no existe
     * @throws IllegalArgumentException si el monto no es positivo o se viola
     *         una regla del tipo de cuenta
     */
    public Optional<Movement> deposit(String accountId, BigDecimal amount) {
        requirePositiveAmount(amount);
        return accountRepository.findById(accountId).map(account -> {
            applyMovementRules(account);
            account.setBalance(account.getBalance().add(amount));
            return recordMovement(account, Movement.TYPE_DEPOSIT, amount);
        });
    }

    /**
     * Registra un retiro de una cuenta.
     * Rechaza el retiro si el saldo es insuficiente.
     *
     * @param accountId identificador de la cuenta
     * @param amount monto a retirar (mayor a cero)
     * @return el movimiento registrado, o empty si la cuenta no existe
     * @throws IllegalArgumentException si el monto no es positivo, el saldo
     *         es insuficiente o se viola una regla del tipo de cuenta
     */
    public Optional<Movement> withdraw(String accountId, BigDecimal amount) {
        requirePositiveAmount(amount);
        return accountRepository.findById(accountId).map(account -> {
            if (account.getBalance().compareTo(amount) < 0) {
                throw new IllegalArgumentException("Saldo insuficiente para el retiro");
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
     * @return lista de movimientos, o empty si la cuenta no existe
     */
    public Optional<List<Movement>> findMovements(String accountId) {
        if (!accountRepository.existsById(accountId)) {
            return Optional.empty();
        }
        return Optional.of(movementRepository.findByAccountIdOrderByOccurredAtDesc(accountId));
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
     * Convierte una lista de entidades Movement a DTOs de respuesta.
     *
     * @param movements lista de entidades
     * @return lista de DTOs
     */
    public List<MovementResponse> toMovementResponseList(List<Movement> movements) {
        return movements.stream()
                .map(this::toMovementResponse)
                .collect(Collectors.toList());
    }

    private void requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
    }

    private void applyMovementRules(Account account) {
        if (account instanceof SavingsAccount savings
                && countCurrentMonthMovements(account.getId()) >= savings.getMonthlyMovementLimit()) {
            throw new IllegalArgumentException(
                    "Se alcanzo el limite de movimientos mensuales de la cuenta de ahorro");
        }
        if (account instanceof FixedTermAccount fixedTerm) {
            if (LocalDate.now().getDayOfMonth() != fixedTerm.getAllowedDayOfMonth()) {
                throw new IllegalArgumentException("La cuenta a plazo fijo solo permite movimientos el dia "
                        + fixedTerm.getAllowedDayOfMonth() + " de cada mes");
            }
            if (countCurrentMonthMovements(account.getId()) > 0) {
                throw new IllegalArgumentException(
                        "La cuenta a plazo fijo permite un solo movimiento por mes");
            }
        }
    }

    private long countCurrentMonthMovements(String accountId) {
        YearMonth current = YearMonth.now();
        return movementRepository.countByAccountIdAndOccurredAtBetween(
                accountId,
                current.atDay(1).atStartOfDay(),
                current.atEndOfMonth().atTime(LocalTime.MAX));
    }

    private Movement recordMovement(Account account, String movementType, BigDecimal amount) {
        accountRepository.save(account);
        Movement movement = movementRepository.save(
                new Movement(account.getId(), movementType, amount));
        accountEventProducer.publishMovementRecorded(movement, account.getAccountType());
        return movement;
    }
}
