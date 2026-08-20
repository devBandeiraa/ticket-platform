package com.devbandeiraa.apigateway;

import com.devbandeiraa.apigateway.support.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Teste de fumaca: garante que o contexto sobe com a tabela de rotas montada.
 *
 * <p>Vale mais do que parece aqui. As rotas sao configuracao, e uma expressao errada em
 * {@code key-resolver} ou um filtro com nome inexistente so aparecem quando o gateway tenta
 * resolve-los na subida — nao ha compilador conferindo o yml.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
