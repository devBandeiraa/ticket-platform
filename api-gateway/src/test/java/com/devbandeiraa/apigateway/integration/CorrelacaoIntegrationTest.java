package com.devbandeiraa.apigateway.integration;

import static com.devbandeiraa.apigateway.support.ServicosDeMentira.EVENT;
import static com.devbandeiraa.apigateway.support.ServicosDeMentira.proximaRequisicao;
import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.apigateway.support.GeradorDeToken;
import com.devbandeiraa.apigateway.support.ServicosDeMentira;
import com.devbandeiraa.apigateway.support.TestcontainersConfig;
import com.devbandeiraa.shared.security.CorrelacaoDeRequisicao;
import java.util.UUID;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Correlacao de requisicoes: geracao, propagacao e devolucao do {@code X-Request-Id}.
 *
 * <p>Como no {@link RoteamentoIntegrationTest}, os limites de requisicao ficam altos para que
 * nenhum teste daqui esbarre no rate limiter por acidente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "RATE_LIMIT_POR_SEGUNDO=1000",
        "RATE_LIMIT_RAJADA=1000",
        "RATE_LIMIT_CUSTO=1",
        "RATE_LIMIT_LOGIN_REPOSICAO=1000",
        "RATE_LIMIT_LOGIN_RAJADA=1000",
        "RATE_LIMIT_LOGIN_CUSTO=1"
})
class CorrelacaoIntegrationTest {

    @Autowired
    private WebTestClient cliente;

    @DynamicPropertySource
    static void apontarParaOsServicosDeMentira(DynamicPropertyRegistry registro) {
        ServicosDeMentira.registrarEnderecos(registro);
    }

    @BeforeEach
    void limparTrafegoAnterior() throws InterruptedException {
        ServicosDeMentira.limpar();
    }

    @Test
    @DisplayName("requisicao sem o cabecalho recebe um id gerado no gateway")
    void deveGerarQuandoNaoVem() throws InterruptedException {
        cliente.get().uri("/api/events").exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getHeader(CorrelacaoDeRequisicao.CABECALHO))
                .as("nenhuma requisicao pode chegar ao servico sem identificacao")
                .isNotNull()
                .matches("[a-f0-9]{16}");
    }

    @Test
    @DisplayName("id enviado pelo cliente e preservado ate o servico")
    void devePropagarOIdDoCliente() throws InterruptedException {
        // Aceitar o id de fora e o que mantem a corrente inteira quando ha um balanceador ou um
        // app movel que ja gera o proprio identificador.
        String id = "cliente-abc-123";

        cliente.get().uri("/api/events")
                .header(CorrelacaoDeRequisicao.CABECALHO, id)
                .exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getHeader(CorrelacaoDeRequisicao.CABECALHO)).isEqualTo(id);
    }

    @Test
    @DisplayName("id fora do formato aceito e trocado, nao encaminhado")
    void deveDescartarIdMalformado() throws InterruptedException {
        // O valor recebido vai parar em toda linha de log do servico. Encaminhar texto arbitrario
        // permitiria escrever entradas falsas no log com aparencia de legitimas — log forging.
        cliente.get().uri("/api/events")
                .header(CorrelacaoDeRequisicao.CABECALHO, "id com espacos e ponto e virgula;")
                .exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();

        // Um unico valor: o recusado nao pode viajar ao lado do substituto, porque o servico de
        // destino leria o primeiro da lista.
        assertThat(recebida.getHeaders().values(CorrelacaoDeRequisicao.CABECALHO))
                .hasSize(1)
                .allMatch(valor -> valor.matches("[a-f0-9]{16}"));
    }

    @Test
    @DisplayName("a resposta devolve o id, uma vez so")
    void deveDevolverOIdAoCliente() {
        String id = "resposta-de-volta";

        cliente.get().uri("/api/events")
                .header(CorrelacaoDeRequisicao.CABECALHO, id)
                .exchange()
                .expectStatus().isOk()
                // O servico de destino tambem devolve o cabecalho, e o gateway copia os cabecalhos
                // recebidos para a resposta final. Sem a sobrescrita no beforeCommit, o cliente
                // receberia o mesmo cabecalho duas vezes.
                .expectHeader().valueEquals(CorrelacaoDeRequisicao.CABECALHO, id);
    }

    @Test
    @DisplayName("erro produzido pelo proprio gateway carrega o id no corpo")
    void deveIdentificarOErroDaBorda() throws InterruptedException {
        String id = "token-recusado-1";

        cliente.get().uri("/api/events")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + GeradorDeToken.expirado(UUID.randomUUID()))
                .header(CorrelacaoDeRequisicao.CABECALHO, id)
                .exchange()
                .expectStatus().isUnauthorized()
                // Este e o ponto do filtro de correlacao vir antes do de autenticacao: a requisicao
                // recusada na borda e justamente a que alguem vai querer investigar, e ela nunca
                // chega a servico nenhum para ganhar um id la.
                .expectBody().jsonPath("$.traceId").isEqualTo(id);

        assertThat(proximaRequisicao(EVENT)).isNull();
    }
}
