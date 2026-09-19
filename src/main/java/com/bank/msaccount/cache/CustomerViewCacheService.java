package com.bank.msaccount.cache;

import com.bank.msaccount.model.CustomerView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Servicio de caché Redis para la vista de cliente.
 * Almacena CustomerView en Redis con TTL de 10 minutos
 * para reducir consultas a MongoDB en lecturas frecuentes.
 */
@Service
@RequiredArgsConstructor
public class CustomerViewCacheService {

    private static final String KEY_PREFIX = "account:customer:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    /**
     * Busca un CustomerView en caché.
     *
     * @param customerId identificador del cliente
     * @return Mono con el CustomerView o vacío si no está en caché
     */
    public Mono<CustomerView> get(String customerId) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + customerId)
                .filter(obj -> obj instanceof CustomerView)
                .map(obj -> (CustomerView) obj);
    }

    /**
     * Almacena un CustomerView en caché.
     *
     * @param customerView vista a almacenar
     * @return Mono<Void> completado cuando se almacena
     */
    public Mono<Void> put(CustomerView customerView) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + customerView.getCustomerId(), customerView, TTL)
                .then();
    }

    /**
     * Elimina un CustomerView de caché.
     *
     * @param customerId identificador del cliente
     * @return Mono<Long> con el número de claves eliminadas
     */
    public Mono<Long> evict(String customerId) {
        return redisTemplate.delete(KEY_PREFIX + customerId);
    }
}
