package com.devbandeiraa.apigateway.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * Sobe um Redis descartavel para os testes.
 *
 * <p>Redis de verdade, e nao um dublê: o rate limiter e um script Lua de token bucket executado
 * dentro do servidor, e e justamente a aritmetica dele que se quer verificar. Um dublê devolveria
 * o que o teste mandasse devolver, e passaria igual se o script estivesse errado.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
