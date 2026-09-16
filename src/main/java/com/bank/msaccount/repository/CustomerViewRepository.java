package com.bank.msaccount.repository;

import com.bank.msaccount.model.CustomerView;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/**
 * Repositorio reactivo para la vista de lectura local de clientes.
 * Se alimenta exclusivamente de los eventos bank.customer.created.
 */
public interface CustomerViewRepository extends ReactiveMongoRepository<CustomerView, String> {
}
