package com.bank.msaccount.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Cuenta corriente.
 * Cliente personal: maximo una. Cliente empresarial: N cuentas,
 * con uno o mas titulares y cero o mas firmantes autorizados.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "account")
public class CheckingAccount extends Account {

    private List<String> holderIds;

    private List<String> signerIds;

    /**
     * Constructor para crear una cuenta corriente.
     *
     * @param customerId identificador del cliente que abre la cuenta
     * @param accountNumber numero de cuenta unico
     * @param holderIds identificadores de los titulares (al menos uno)
     * @param signerIds identificadores de los firmantes autorizados (puede ser vacia)
     */
    public CheckingAccount(String customerId, String accountNumber,
                           List<String> holderIds, List<String> signerIds) {
        super(customerId, accountNumber, TYPE_CHECKING);
        this.holderIds = holderIds;
        this.signerIds = signerIds;
    }
}
