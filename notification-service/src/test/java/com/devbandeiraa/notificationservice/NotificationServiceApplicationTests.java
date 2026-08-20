package com.devbandeiraa.notificationservice;

import com.devbandeiraa.notificationservice.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Teste de fumaca: garante que o contexto sobe, que as filas e o exchange sao declarados no
 * broker e que o listener se registra.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class NotificationServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
