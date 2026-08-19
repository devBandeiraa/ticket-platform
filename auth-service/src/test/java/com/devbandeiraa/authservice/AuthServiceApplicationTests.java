package com.devbandeiraa.authservice;

import com.devbandeiraa.authservice.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Teste de fumaca: garante que o contexto do Spring sobe por inteiro, com o datasource
 * conectado e as migrations do Flyway aplicadas.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class AuthServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
