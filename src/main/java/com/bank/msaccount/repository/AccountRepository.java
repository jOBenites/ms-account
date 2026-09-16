package com.bank.msaccount.repository;

import com.bank.msaccount.model.Account;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

/**
 * Repositorio reactivo para la entidad Account en MongoDB.
 * No se permite @Query ni consultas dinamicas segun las reglas del proyecto.
 */
public interface AccountRepository extends ReactiveMongoRepository<Account, String> {

    /**
     * Cuenta las cuentas de un tipo que posee un cliente.
     * Se usa para validar las reglas de cardinalidad al abrir cuentas.
     *
     * @param customerId identificador del cliente
     * @param accountType tipo de cuenta (SAVINGS, CHECKING o FIXED_TERM)
     * @return Mono con el numero de cuentas del tipo indicado
     */
    Mono<Long> countByCustomerIdAndAccountType(String customerId, String accountType);
}
