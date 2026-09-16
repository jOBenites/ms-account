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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Servicio reactivo de gestion de cuentas bancarias.
 * Expone CRUD completo y apertura de cuentas aplicando las reglas de
 * cardinalidad por tipo de cliente:
 * personal (max. 1 ahorro, 1 corriente, N plazo fijo) y
 * empresarial (solo corrientes, N, con 1+ titulares y 0+ firmantes).
 * La validacion del tipo de cliente usa la vista local customer_view
 * alimentada por eventos, sin llamadas REST a ms-customer.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final String CUSTOMER_TYPE_PERSONAL = "PERSONAL";

    private final AccountRepository accountRepository;
    private final CustomerViewRepository customerViewRepository;
    private final AccountEventProducer accountEventProducer;

    @Value("${account.minimum-opening-amount:0}")
    private BigDecimal minimumOpeningAmount;

    /**
     * Establece el monto minimo de apertura de cuenta.
     * Util para pruebas y configuracion dinamica.
     *
     * @param minimumOpeningAmount nuevo monto minimo
     */
    public void setMinimumOpeningAmount(BigDecimal minimumOpeningAmount) {
        this.minimumOpeningAmount = minimumOpeningAmount;
    }

    /**
     * Abre una cuenta de ahorro para un cliente personal.
     * Un cliente personal solo puede tener una cuenta de ahorro.
     * El saldo inicial debe ser mayor o igual al monto minimo de apertura.
     *
     * @param customerId identificador del cliente titular
     * @param initialBalance saldo inicial de la cuenta (nullable, por defecto 0)
     * @return Mono con la cuenta de ahorro creada
     */
    public Mono<SavingsAccount> openSavingsAccount(String customerId, BigDecimal initialBalance) {
        return validateInitialBalance(initialBalance)
                .then(requirePersonalCustomer(customerId, "La cuenta de ahorro"))
                .then(accountRepository.countByCustomerIdAndAccountType(customerId, Account.TYPE_SAVINGS))
                .flatMap(count -> {
                    if (count > 0) {
                        return Mono.error(new IllegalArgumentException("El cliente ya tiene una cuenta de ahorro"));
                    }
                    SavingsAccount account = new SavingsAccount(customerId, generateAccountNumber());
                    if (initialBalance != null && initialBalance.signum() > 0) {
                        account.setBalance(initialBalance);
                    }
                    return accountRepository.save(account)
                            .doOnNext(accountEventProducer::publishAccountOpened);
                });
    }

    /**
     * Abre una cuenta corriente para un cliente personal o empresarial.
     * Cliente personal: maximo una. Cliente empresarial: N cuentas.
     * El saldo inicial debe ser mayor o igual al monto minimo de apertura.
     *
     * @param customerId identificador del cliente que abre la cuenta
     * @param holderIds identificadores de los titulares (nullable, por defecto el cliente)
     * @param signerIds identificadores de los firmantes autorizados (nullable)
     * @param initialBalance saldo inicial de la cuenta (nullable, por defecto 0)
     * @return Mono con la cuenta corriente creada
     */
    public Mono<CheckingAccount> openCheckingAccount(String customerId, List<String> holderIds,
                                                      List<String> signerIds, BigDecimal initialBalance) {
        return validateInitialBalance(initialBalance)
                .then(requireCustomer(customerId))
                .flatMap(customer -> {
                    if (CUSTOMER_TYPE_PERSONAL.equals(customer.getCustomerType())) {
                        return accountRepository.countByCustomerIdAndAccountType(
                                        customerId, Account.TYPE_CHECKING)
                                .flatMap(count -> {
                                    if (count > 0) {
                                        return Mono.error(new IllegalArgumentException(
                                                "El cliente personal ya tiene una cuenta corriente"));
                                    }
                                    return createCheckingAccount(customerId, holderIds, signerIds,
                                            customer, initialBalance);
                                });
                    }
                    return createCheckingAccount(customerId, holderIds, signerIds, customer, initialBalance);
                });
    }

    /**
     * Abre una cuenta a plazo fijo para un cliente personal.
     * Un cliente personal puede tener N cuentas a plazo fijo.
     * El saldo inicial debe ser mayor o igual al monto minimo de apertura.
     *
     * @param customerId identificador del cliente titular
     * @param allowedDayOfMonth dia del mes en que se permiten movimientos (nullable)
     * @param initialBalance saldo inicial de la cuenta (nullable, por defecto 0)
     * @return Mono con la cuenta a plazo fijo creada
     */
    public Mono<FixedTermAccount> openFixedTermAccount(String customerId, Integer allowedDayOfMonth,
                                                        BigDecimal initialBalance) {
        return validateInitialBalance(initialBalance)
                .then(requirePersonalCustomer(customerId, "La cuenta a plazo fijo"))
                .then(Mono.fromCallable(() -> {
                    FixedTermAccount account = new FixedTermAccount(
                            customerId, generateAccountNumber(), allowedDayOfMonth);
                    if (initialBalance != null && initialBalance.signum() > 0) {
                        account.setBalance(initialBalance);
                    }
                    return account;
                }))
                .flatMap(accountRepository::save)
                .doOnNext(accountEventProducer::publishAccountOpened);
    }

    /**
     * Busca una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return Mono con la cuenta encontrada o vacio
     */
    public Mono<Account> findById(String id) {
        return accountRepository.findById(id);
    }

    /**
     * Lista todas las cuentas registradas.
     *
     * @return Flux con las cuentas
     */
    public Flux<Account> findAll() {
        return accountRepository.findAll();
    }

    /**
     * Actualiza los titulares y firmantes de una cuenta corriente.
     *
     * @param id identificador de la cuenta
     * @param holderIds nuevos titulares (nullable, mantiene los actuales)
     * @param signerIds nuevos firmantes (nullable, mantiene los actuales)
     * @return Mono con la cuenta actualizada, o vacio si no se encontro
     */
    public Mono<Account> update(String id, List<String> holderIds, List<String> signerIds) {
        return accountRepository.findById(id)
                .flatMap(account -> {
                    if (!(account instanceof CheckingAccount checking)) {
                        return Mono.error(new IllegalArgumentException(
                                "Solo las cuentas corrientes tienen titulares y firmantes"));
                    }
                    if (holderIds != null) {
                        if (holderIds.isEmpty()) {
                            return Mono.error(new IllegalArgumentException(
                                    "La cuenta corriente debe tener al menos un titular"));
                        }
                        checking.setHolderIds(holderIds);
                    }
                    if (signerIds != null) {
                        checking.setSignerIds(signerIds);
                    }
                    return accountRepository.save(checking);
                });
    }

    /**
     * Elimina una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return Mono con true si se elimino, false si no existia
     */
    public Mono<Boolean> delete(String id) {
        return accountRepository.existsById(id)
                .flatMap(exists -> {
                    if (exists) {
                        return accountRepository.deleteById(id).then(Mono.just(true));
                    }
                    return Mono.just(false);
                });
    }

    /**
     * Convierte una entidad Account a su DTO de respuesta.
     *
     * @param account entidad a convertir
     * @return DTO con los campos poblados
     */
    public AccountResponse toResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setAccountNumber(account.getAccountNumber());
        response.setCustomerId(account.getCustomerId());
        response.setAccountType(account.getAccountType());
        response.setBalance(account.getBalance());
        if (account instanceof CheckingAccount checking) {
            response.setHolderIds(checking.getHolderIds());
            response.setSignerIds(checking.getSignerIds());
        }
        if (account instanceof SavingsAccount savings) {
            response.setMonthlyMovementLimit(savings.getMonthlyMovementLimit());
        }
        if (account instanceof FixedTermAccount fixedTerm) {
            response.setAllowedDayOfMonth(fixedTerm.getAllowedDayOfMonth());
        }
        return response;
    }

    /**
     * Convierte un Flux de entidades Account a DTOs de respuesta.
     *
     * @param accounts Flux de entidades
     * @return Flux de DTOs
     */
    public Flux<AccountResponse> toResponseList(Flux<Account> accounts) {
        return accounts.map(this::toResponse);
    }

    private Mono<CheckingAccount> createCheckingAccount(String customerId, List<String> holderIds,
                                                          List<String> signerIds, CustomerView customer,
                                                          BigDecimal initialBalance) {
        List<String> holders = (holderIds == null || holderIds.isEmpty()) ? List.of(customerId) : holderIds;
        List<String> signers = signerIds == null ? List.of() : signerIds;
        CheckingAccount account = new CheckingAccount(customerId, generateAccountNumber(), holders, signers);
        if (initialBalance != null && initialBalance.signum() > 0) {
            account.setBalance(initialBalance);
        }
        return accountRepository.save(account)
                .doOnNext(accountEventProducer::publishAccountOpened);
    }

    private Mono<CustomerView> requireCustomer(String customerId) {
        return customerViewRepository.findById(customerId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Cliente no encontrado o no sincronizado: " + customerId)));
    }

    private Mono<CustomerView> requirePersonalCustomer(String customerId, String product) {
        return requireCustomer(customerId)
                .flatMap(customer -> {
                    if (!CUSTOMER_TYPE_PERSONAL.equals(customer.getCustomerType())) {
                        return Mono.error(new IllegalArgumentException(
                                product + " solo esta disponible para clientes personales"));
                    }
                    return Mono.just(customer);
                });
    }

    private String generateAccountNumber() {
        return String.format("%012d", ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L));
    }

    private Mono<Void> validateInitialBalance(BigDecimal initialBalance) {
        if (initialBalance != null && initialBalance.compareTo(minimumOpeningAmount) < 0) {
            return Mono.error(new IllegalArgumentException(
                    "El saldo inicial debe ser mayor o igual al monto minimo de apertura: " + minimumOpeningAmount));
        }
        return Mono.empty();
    }

    /**
     * Valida que el saldo inicial cumpla con el monto minimo de apertura.
     * Visibilidad de paquete para pruebas.
     *
     * @param initialBalance saldo inicial a validar
     * @return Mono.empty() si es valido, Mono.error() si no cumple
     */
    Mono<Void> validateInitialBalanceForTest(BigDecimal initialBalance) {
        return validateInitialBalance(initialBalance);
    }
}
