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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Servicio de gestion de cuentas bancarias.
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

    /**
     * Abre una cuenta de ahorro para un cliente personal.
     * Un cliente personal solo puede tener una cuenta de ahorro.
     *
     * @param customerId identificador del cliente titular
     * @return la cuenta de ahorro creada
     * @throws IllegalArgumentException si el cliente no existe, no es personal
     *         o ya tiene una cuenta de ahorro
     */
    public SavingsAccount openSavingsAccount(String customerId) {
        requirePersonalCustomer(customerId, "La cuenta de ahorro");
        if (accountRepository.countByCustomerIdAndAccountType(customerId, Account.TYPE_SAVINGS) > 0) {
            throw new IllegalArgumentException("El cliente ya tiene una cuenta de ahorro");
        }
        SavingsAccount account = new SavingsAccount(customerId, generateAccountNumber());
        SavingsAccount saved = accountRepository.save(account);
        accountEventProducer.publishAccountOpened(saved);
        return saved;
    }

    /**
     * Abre una cuenta corriente para un cliente personal o empresarial.
     * Cliente personal: maximo una. Cliente empresarial: N cuentas.
     * Si no se indican titulares, el titular es el cliente que abre la cuenta.
     *
     * @param customerId identificador del cliente que abre la cuenta
     * @param holderIds identificadores de los titulares (nullable, por defecto el cliente)
     * @param signerIds identificadores de los firmantes autorizados (nullable)
     * @return la cuenta corriente creada
     * @throws IllegalArgumentException si el cliente no existe o es personal
     *         y ya tiene una cuenta corriente
     */
    public CheckingAccount openCheckingAccount(String customerId, List<String> holderIds, List<String> signerIds) {
        CustomerView customer = requireCustomer(customerId);
        if (CUSTOMER_TYPE_PERSONAL.equals(customer.getCustomerType())
                && accountRepository.countByCustomerIdAndAccountType(customerId, Account.TYPE_CHECKING) > 0) {
            throw new IllegalArgumentException("El cliente personal ya tiene una cuenta corriente");
        }
        List<String> holders = (holderIds == null || holderIds.isEmpty()) ? List.of(customerId) : holderIds;
        List<String> signers = signerIds == null ? List.of() : signerIds;
        CheckingAccount account = new CheckingAccount(customerId, generateAccountNumber(), holders, signers);
        CheckingAccount saved = accountRepository.save(account);
        accountEventProducer.publishAccountOpened(saved);
        return saved;
    }

    /**
     * Abre una cuenta a plazo fijo para un cliente personal.
     * Un cliente personal puede tener N cuentas a plazo fijo.
     *
     * @param customerId identificador del cliente titular
     * @return la cuenta a plazo fijo creada
     * @throws IllegalArgumentException si el cliente no existe o no es personal
     */
    public FixedTermAccount openFixedTermAccount(String customerId) {
        requirePersonalCustomer(customerId, "La cuenta a plazo fijo");
        FixedTermAccount account = new FixedTermAccount(customerId, generateAccountNumber());
        FixedTermAccount saved = accountRepository.save(account);
        accountEventProducer.publishAccountOpened(saved);
        return saved;
    }

    /**
     * Busca una cuenta por su ID.
     *
     * @param id identificador de la cuenta
     * @return optional con la cuenta encontrada
     */
    public Optional<Account> findById(String id) {
        return accountRepository.findById(id);
    }

    /**
     * Lista todas las cuentas registradas.
     *
     * @return lista de cuentas
     */
    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    /**
     * Actualiza los titulares y firmantes de una cuenta corriente.
     * Una cuenta corriente debe conservar al menos un titular.
     *
     * @param id identificador de la cuenta
     * @param holderIds nuevos titulares (nullable, mantiene los actuales)
     * @param signerIds nuevos firmantes (nullable, mantiene los actuales)
     * @return la cuenta actualizada, o empty si no se encontro
     * @throws IllegalArgumentException si la cuenta no es corriente
     *         o la lista de titulares queda vacia
     */
    public Optional<Account> update(String id, List<String> holderIds, List<String> signerIds) {
        return accountRepository.findById(id).map(account -> {
            if (!(account instanceof CheckingAccount checking)) {
                throw new IllegalArgumentException("Solo las cuentas corrientes tienen titulares y firmantes");
            }
            if (holderIds != null) {
                if (holderIds.isEmpty()) {
                    throw new IllegalArgumentException("La cuenta corriente debe tener al menos un titular");
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
     * @return true si se elimino, false si no existia
     */
    public boolean delete(String id) {
        if (accountRepository.existsById(id)) {
            accountRepository.deleteById(id);
            return true;
        }
        return false;
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
        return response;
    }

    /**
     * Convierte una lista de entidades Account a DTOs de respuesta.
     *
     * @param accounts lista de entidades
     * @return lista de DTOs
     */
    public List<AccountResponse> toResponseList(List<Account> accounts) {
        return accounts.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private CustomerView requireCustomer(String customerId) {
        return customerViewRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Cliente no encontrado o no sincronizado: " + customerId));
    }

    private CustomerView requirePersonalCustomer(String customerId, String product) {
        CustomerView customer = requireCustomer(customerId);
        if (!CUSTOMER_TYPE_PERSONAL.equals(customer.getCustomerType())) {
            throw new IllegalArgumentException(product + " solo esta disponible para clientes personales");
        }
        return customer;
    }

    private String generateAccountNumber() {
        return String.format("%012d", ThreadLocalRandom.current().nextLong(100_000_000_000L, 1_000_000_000_000L));
    }
}
