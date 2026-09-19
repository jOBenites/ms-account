package com.bank.msaccount.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Servicio de caché Redis para configuraciones de negocio.
 * Almacena parametros de cuenta (comisiones, limites, promedio minimo VIP)
 * en Redis para evitar lecturas repetidas a Config Server.
 */
@Service
@RequiredArgsConstructor
public class AccountConfigCacheService {

    private static final String KEY_PREFIX = "account:config:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    /**
     * Obtiene un valor de configuración por nombre de clave.
     *
     * @param key nombre del parámetro (ej. "minimum-opening-amount")
     * @return Mono con el valor BigDecimal o vacío si no existe
     */
    public Mono<BigDecimal> get(String key) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + key)
                .filter(obj -> obj instanceof BigDecimal)
                .map(obj -> (BigDecimal) obj);
    }

    /**
     * Almacena un valor de configuración en caché.
     *
     * @param key nombre del parámetro
     * @param value valor del parámetro
     * @return Mono<Void> completado cuando se almacena
     */
    public Mono<Void> put(String key, BigDecimal value) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + key, value, TTL)
                .then();
    }

    /**
     * Elimina todas las configuraciones de cuenta de caché.
     *
     * @return Mono<Long> con el número de claves eliminadas
     */
    public Mono<Long> evictAll() {
        return redisTemplate.keys(KEY_PREFIX + "*")
                .flatMap(redisTemplate::delete)
                .reduce(0L, Long::sum);
    }
}
