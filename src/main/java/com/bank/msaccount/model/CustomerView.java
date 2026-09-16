package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Vista de lectura local del cliente, alimentada por el evento
 * bank.customer.created publicado por ms-customer.
 * Permite validar tipo y perfil del cliente al abrir cuentas
 * sin llamadas REST entre microservicios (database-per-service).
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "customer_view")
public class CustomerView {

    @Id
    private String customerId;

    private String customerType;

    private String profile;

    private String documentNumber;

    /**
     * Constructor completo de la vista de cliente.
     *
     * @param customerId identificador del cliente
     * @param customerType tipo de cliente (PERSONAL o BUSINESS)
     * @param profile perfil del cliente (REGULAR, VIP o PYME)
     * @param documentNumber numero de documento del cliente
     */
    public CustomerView(String customerId, String customerType, String profile, String documentNumber) {
        this.customerId = customerId;
        this.customerType = customerType;
        this.profile = profile;
        this.documentNumber = documentNumber;
    }
}
