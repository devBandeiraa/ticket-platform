package com.devbandeiraa.bookingservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Sobe a infraestrutura descartavel dos testes de integracao.
 *
 * <p>Os tres containers ficam juntos e sao importados de uma vez porque todo teste de integracao
 * deste servico precisa dos tres — o contexto do booking-service nao sobe sem banco, e sobe
 * reclamando sem Redis ou sem broker. Uma lista repetida em cada classe de teste tenderia a
 * divergir, e o custo real e nulo: como todas as classes declaram exatamente a mesma
 * configuracao, o Spring reaproveita um unico contexto e os containers sobem uma vez so para a
 * suite inteira.
 *
 * <p>Nada de substitutos em memoria, e a razao e o proprio objeto deste servico. O que se precisa
 * provar aqui e que o PostgreSQL recusa o overselling, que o Redis executa o script de liberacao
 * atomicamente e que a mensagem chega ao broker. Nenhuma dessas propriedades pertence ao cliente:
 * sao do servidor, e um dublê teria exatamente o comportamento esperado dele, inclusive quando o
 * servidor real divergisse.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }

    /**
     * {@code GenericContainer} em vez de um modulo dedicado: o Redis nao precisa de nada alem da
     * porta exposta, e {@code @ServiceConnection} cuida de apontar {@code spring.data.redis.*}
     * para o container.
     */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer("rabbitmq:4-alpine");
    }
}
