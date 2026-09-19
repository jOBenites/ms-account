package com.bank.msaccount.repository;

import com.bank.msaccount.model.DebtStatusView;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

/**
 * Repositorio reactivo para la vista de estado de deuda vencida.
 * Se alimenta exclusivamente de los eventos bank.debt.overdue-detected
 * y bank.debt.settled.
 */
public interface DebtStatusViewRepository extends ReactiveMongoRepository<DebtStatusView, String> {
}
