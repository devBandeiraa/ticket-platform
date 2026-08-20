package com.devbandeiraa.bookingservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe um PostgreSQL descartavel para os testes de integracao.
 *
 * <p>Aqui o banco real nao e apenas conveniencia, e requisito: o que este servico precisa provar
 * e que o proprio PostgreSQL recusa o overselling. Um banco em memoria com semantica de
 * isolamento diferente poderia passar em todos os testes e ainda assim vender ingresso a mais em
 * producao.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
