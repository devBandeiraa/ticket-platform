package com.devbandeiraa.eventservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe um PostgreSQL descartavel para os testes de integracao.
 *
 * <p>Cada execucao parte de um banco vazio no qual o Flyway aplica as migrations do zero, o que
 * de quebra valida as proprias migrations a cada rodada. Os testes nao dependem do
 * docker-compose estar no ar nem deixam registros no banco de desenvolvimento.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
