package com.devbandeiraa.eventservice;

import com.devbandeiraa.eventservice.support.PostgresContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Teste de fumaca: garante que o contexto sobe por inteiro, com o datasource conectado, as
 * migrations do Flyway aplicadas e a cadeia de seguranca montada.
 */
@SpringBootTest
@Import(PostgresContainerConfig.class)
class EventServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
