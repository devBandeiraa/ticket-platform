package com.devbandeiraa.bookingservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * Sobe um Redis descartavel para os testes do lock.
 *
 * <p>Simular o Redis serviria para verificar o fluxo, mas nao o que realmente importa aqui: a
 * atomicidade do {@code SET NX PX} e a do script Lua de liberacao sao propriedades do servidor,
 * nao do cliente. Um dublê teria exatamente o comportamento que o teste espera dele, inclusive
 * quando o servidor real divergisse.
 *
 * <p>Ha um {@code GenericContainer} em vez de um modulo dedicado porque o Redis nao precisa de
 * nada alem da porta exposta; {@code @ServiceConnection} cuida de apontar
 * {@code spring.data.redis.*} para o container.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisContainerConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
