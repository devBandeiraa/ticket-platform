package com.devbandeiraa.bookingservice;

import com.devbandeiraa.bookingservice.support.PostgresContainerConfig;
import com.devbandeiraa.bookingservice.support.RedisContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de fumaca: garante que o contexto sobe por inteiro, com o datasource conectado e as
 * migrations do Flyway aplicadas.
 *
 * <p>Com {@code ddl-auto: validate}, subir o contexto ja e um teste: se uma entidade divergir da
 * migration — coluna renomeada, tipo trocado, campo novo sem DDL — a aplicacao nao inicia.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
@ActiveProfiles("test")
class BookingServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
