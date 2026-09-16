package com.bank.msaccount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Microservicio de gestion de cuentas bancarias del sistema bancario.
 * Expone CRUD completo y apertura de cuentas de ahorro, corrientes y a plazo fijo,
 * aplica las reglas de cardinalidad por tipo de cliente y publica el evento
 * bank.account.opened al abrir una nueva cuenta.
 */
@EnableMongoAuditing
@SpringBootApplication
public class MsAccountApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAccountApplication.class, args);
    }
}
