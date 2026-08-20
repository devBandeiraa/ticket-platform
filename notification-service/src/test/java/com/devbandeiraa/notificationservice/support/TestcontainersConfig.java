package com.devbandeiraa.notificationservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Sobe broker e Redis descartaveis para os testes.
 *
 * <p>Um broker simulado nao serviria: o que se verifica aqui e roteamento, retentativa e
 * dead-lettering, e as tres sao propriedades do RabbitMQ, nao do cliente. Um dublê se comportaria
 * exatamente como o teste espera, inclusive quando o servidor real divergisse.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:4-alpine");
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }
}
