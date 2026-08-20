package com.devbandeiraa.bookingservice.config;

import com.devbandeiraa.bookingservice.lock.LockProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita os ajustes do lock distribuido.
 *
 * <p>Nao ha bean de {@code StringRedisTemplate} declarado aqui: o Spring Boot ja o fornece a
 * partir de {@code spring.data.redis.*}. Redeclarar so acrescentaria um ponto a mais para
 * divergir da configuracao padrao.
 */
@Configuration
@EnableConfigurationProperties(LockProperties.class)
public class LockConfig {
}
