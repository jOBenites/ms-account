# ms-account

Microservicio de gestión de cuentas bancarias (ahorro, corriente, plazo fijo). Aplica las reglas de cardinalidad por tipo de cliente, expone CRUD completo, consume el evento `bank.customer.created` para validar clientes sin REST entre microservicios, y publica `bank.account.opened` al abrir una cuenta.

## Reglas de negocio (Fase I)

| Tipo de cuenta | Cliente personal | Cliente empresarial |
|----------------|------------------|---------------------|
| Ahorro | Máx. 1 | No permitida |
| Corriente | Máx. 1 | N, con 1+ titulares y 0+ firmantes |
| Plazo fijo | N | No permitida |

La apertura valida el tipo de cliente contra la vista local `customer_view` (alimentada por Kafka), no contra ms-customer.

## Requisitos

- Java 17
- Maven 3.9+
- MongoDB (puerto 27017)
- Kafka (puerto 9092)
- Config Server corriendo en puerto 8888

## Variables de entorno

El servicio obtiene la configuración desde Config Server. Las variables críticas en `application.yml` local:

| Variable | Valor por defecto | Descripción |
|----------|-------------------|-------------|
| `server.port` | `8082` | Puerto del servicio |
| `spring.config.import` | `optional:configserver:http://localhost:8888` | URL del Config Server |
| `spring.data.mongodb.uri` | `mongodb://...account_db` | URI de MongoDB (fallback si Config Server no está disponible) |
| `spring.kafka.consumer.group-id` | `ms-account` | Grupo consumidor de Kafka |

## Levantar

```bash
mvn spring-boot:run
```

O generar el JAR:

```bash
mvn clean package -DskipTests
java -jar target/ms-account-1.0.0.jar
```

## Endpoints

| Método | Ruta | Descripción |
|--------|------|-------------|
| POST | `/accounts/savings` | Abrir cuenta de ahorro |
| POST | `/accounts/checking` | Abrir cuenta corriente |
| POST | `/accounts/fixed-term` | Abrir cuenta a plazo fijo |
| GET | `/accounts/{id}` | Buscar por ID |
| GET | `/accounts` | Listar todas |
| PUT | `/accounts/{id}` | Actualizar titulares/firmantes (solo corrientes) |
| DELETE | `/accounts/{id}` | Eliminar |

## Base de datos

- **Database:** `account_db`
- **Colecciones:** `account` (con discriminador `_class` para herencia), `customer_view` (vista de lectura local)

## Eventos

| Topic | Dirección | Trigger | Payload |
|-------|-----------|---------|---------|
| `bank.customer.created` | Consume | ms-customer crea un cliente | `customerId`, `customerType`, `profile`, `documentNumber`, `occurredAt` |
| `bank.account.opened` | Produce | Al abrir cualquier cuenta | `accountId`, `customerId`, `accountType`, `occurredAt` |

## Verificar

```bash
mvn verify
```

Ejecuta tests unitarios, Checkstyle y genera reporte JaCoCo.
