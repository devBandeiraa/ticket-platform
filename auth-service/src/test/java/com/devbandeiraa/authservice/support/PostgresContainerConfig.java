package com.devbandeiraa.authservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Sobe um PostgreSQL descartavel para os testes de integracao.
 *
 * <p>Sem isto os testes escreveriam no banco de desenvolvimento: passariam apenas com o
 * docker-compose no ar, deixariam registros para tras e quebrariam em CI. Com o container,
 * cada execucao comeca de um banco vazio no qual o Flyway aplica as migrations do zero — o
 * que de quebra valida as proprias migrations a cada rodada.
 *
 * <p>A anotacao {@code @ServiceConnection} dispensa configurar url, usuario e senha: o Spring
 * Boot aponta o datasource para o container automaticamente.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:17-alpine");
    }
}
