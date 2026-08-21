package com.devbandeiraa.apigateway.integration;

import com.devbandeiraa.apigateway.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * O Swagger UI agregado do gateway.
 *
 * <p>Este arquivo existe por causa de duas falhas reais cometidas ao montar a documentacao, e
 * cada teste aqui prende uma delas. Ambas tinham a mesma assinatura horrivel: build verde, todos
 * os servicos saudaveis, e a pagina de documentacao simplesmente 404. Nada no log.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "RATE_LIMIT_POR_SEGUNDO=1000",
        "RATE_LIMIT_RAJADA=1000",
        "RATE_LIMIT_CUSTO=1"
})
class DocumentacaoAgregadaIntegrationTest {

    @Autowired
    private WebTestClient cliente;

    /**
     * Falha numero um: a dependencia do springdoc acabou dentro de {@code dependencyManagement}
     * em vez de {@code dependencies}. O Maven aceitou sem reclamar — ali a declaracao apenas fixa
     * uma versao — e o jar saiu sem o springdoc. Este teste falha se isso voltar a acontecer.
     */
    @Test
    @DisplayName("o Swagger UI e servido pelo gateway")
    void interfaceEServida() {
        cliente.get().uri("/swagger-ui.html")
                .exchange()
                // 302 para /swagger-ui/index.html: o springdoc redireciona em vez de servir a
                // pagina no caminho curto.
                .expectStatus().isFound();

        cliente.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }

    /**
     * Falha numero dois: com {@code springdoc.api-docs.enabled=false} — que parecia certo, ja que
     * o gateway nao tem controller para documentar — a auto-configuracao inteira desliga, e leva
     * junto este endpoint, de onde a propria pagina le a lista de servicos.
     */
    @Test
    @DisplayName("a configuracao lista os tres servicos que publicam HTTP")
    void configuracaoListaOsServicos() {
        cliente.get().uri("/v3/api-docs/swagger-config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.urls[?(@.url=='/api/auth/v3/api-docs')]").exists()
                .jsonPath("$.urls[?(@.url=='/api/events/v3/api-docs')]").exists()
                .jsonPath("$.urls[?(@.url=='/api/bookings/v3/api-docs')]").exists();
    }

    /**
     * Os caminhos anunciados precisam bater com a tabela de rotas.
     *
     * <p>Sao os mesmos {@code /api/**} do trafego normal, de proposito — a especificacao viaja
     * pela rota que ela descreve. Aqui os servicos de destino nao existem, entao o gateway
     * responde erro de gateway; o que importa e que ele TENTOU encaminhar, e nao respondeu 404
     * por nao reconhecer o caminho. 404 significaria documentacao apontando para o nada.
     */
    @Test
    @DisplayName("os caminhos da documentacao casam com rotas existentes")
    void caminhosCasamComRotas() {
        for (String caminho : new String[]{
                "/api/auth/v3/api-docs",
                "/api/events/v3/api-docs",
                "/api/bookings/v3/api-docs"}) {

            cliente.get().uri(caminho)
                    .exchange()
                    .expectStatus().value(status -> {
                        if (status == 404) {
                            throw new AssertionError(
                                    "nenhuma rota casou com " + caminho + ": a documentacao "
                                            + "agregada apontaria para um caminho inexistente");
                        }
                    });
        }
    }
}
