package com.devbandeiraa.apigateway.integration;

import static com.devbandeiraa.apigateway.support.ServicosDeMentira.AUTH;
import static com.devbandeiraa.apigateway.support.ServicosDeMentira.BOOKING;
import static com.devbandeiraa.apigateway.support.ServicosDeMentira.EVENT;
import static com.devbandeiraa.apigateway.support.ServicosDeMentira.proximaRequisicao;
import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.apigateway.security.CabecalhosDeIdentidade;
import com.devbandeiraa.apigateway.support.GeradorDeToken;
import com.devbandeiraa.apigateway.support.ServicosDeMentira;
import com.devbandeiraa.apigateway.support.TestcontainersConfig;
import com.devbandeiraa.shared.security.Role;
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
 * Roteamento, autenticacao na borda e CORS.
 *
 * <p>Os limites de requisicao ficam altos aqui de proposito, para que nenhum teste destes esbarre
 * neles por acidente. A politica de limite tem teste proprio, em
 * {@link RateLimitIntegrationTest}.
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
class RoteamentoIntegrationTest {

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

    // ---------- roteamento ----------

    @Test
    @DisplayName("o prefixo /api e retirado antes de encaminhar")
    void deveRemoverOPrefixoApi() throws InterruptedException {
        cliente.get().uri("/api/events?page=0").exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        // O servico continua publicando /events: o prefixo e uma convencao do frontend, e
        // nenhum servico precisa saber que existe um gateway na frente.
        assertThat(recebida.getPath()).isEqualTo("/events?page=0");
    }

    @Test
    @DisplayName("cada prefixo chega ao servico dono dele")
    void deveRotearCadaPrefixoParaSeuServico() throws InterruptedException {
        cliente.post().uri("/api/auth/login").exchange().expectStatus().isOk();
        assertThat(proximaRequisicao(AUTH)).isNotNull();

        cliente.get().uri("/api/bookings/me").exchange().expectStatus().isOk();
        assertThat(proximaRequisicao(BOOKING)).isNotNull();

        cliente.get().uri("/api/admin/events").exchange().expectStatus().isOk();
        assertThat(proximaRequisicao(EVENT)).isNotNull();

        cliente.get().uri("/api/admin/bookings").exchange().expectStatus().isOk();
        assertThat(proximaRequisicao(BOOKING)).isNotNull();
    }

    @Test
    @DisplayName("disponibilidade vai para o booking-service, e nao para o catalogo")
    void deveRotearDisponibilidadeParaOBooking() throws InterruptedException {
        UUID evento = UUID.randomUUID();

        cliente.get().uri("/api/events/{id}/availability", evento).exchange().expectStatus().isOk();

        // O unico caminho sob /events que nao pertence ao event-service: o contador de estoque
        // mora no booking-service. Basta inverter a ordem das rotas no yml para este teste cair.
        assertThat(proximaRequisicao(BOOKING)).isNotNull();
        assertThat(proximaRequisicao(EVENT)).isNull();
    }

    @Test
    @DisplayName("o catalogo continua indo para o event-service")
    void deveRotearOCatalogoParaOEvent() throws InterruptedException {
        UUID evento = UUID.randomUUID();

        cliente.get().uri("/api/events/{id}", evento).exchange().expectStatus().isOk();

        assertThat(proximaRequisicao(EVENT)).isNotNull();
        assertThat(proximaRequisicao(BOOKING)).isNull();
    }

    @Test
    @DisplayName("caminho fora da tabela de rotas nao chega a servico nenhum")
    void deveRecusarCaminhoDesconhecido() throws InterruptedException {
        cliente.get().uri("/api/inexistente").exchange().expectStatus().isNotFound();

        assertThat(proximaRequisicao(AUTH)).isNull();
        assertThat(proximaRequisicao(EVENT)).isNull();
        assertThat(proximaRequisicao(BOOKING)).isNull();
    }

    // ---------- identidade ----------

    @Test
    @DisplayName("token valido vira cabecalhos de identidade")
    void deveInjetarAIdentidade() throws InterruptedException {
        UUID usuario = UUID.randomUUID();
        String token = GeradorDeToken.valido(usuario, "maria@teste.com", Role.ADMIN);

        cliente.get().uri("/api/admin/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getHeader(CabecalhosDeIdentidade.USER_ID)).isEqualTo(usuario.toString());
        assertThat(recebida.getHeader(CabecalhosDeIdentidade.USER_EMAIL)).isEqualTo("maria@teste.com");
        assertThat(recebida.getHeader(CabecalhosDeIdentidade.USER_ROLE)).isEqualTo("ADMIN");

        // O Authorization segue adiante: o servico valida o token por conta propria, e e isso
        // que impede que alcanca-lo por dentro da rede contorne a autenticacao.
        assertThat(recebida.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer " + token);
    }

    @Test
    @DisplayName("cabecalho de identidade forjado pelo cliente e descartado")
    void deveApagarIdentidadeForjada() throws InterruptedException {
        UUID intruso = UUID.randomUUID();

        // Sem esta protecao, qualquer um se declararia ADMIN e o event-service, se confiasse no
        // cabecalho, obedeceria. E a falha classica de gateway que aceita o proprio cabecalho.
        cliente.get().uri("/api/events")
                .header(CabecalhosDeIdentidade.USER_ID, intruso.toString())
                .header(CabecalhosDeIdentidade.USER_ROLE, "ADMIN")
                .exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getHeader(CabecalhosDeIdentidade.USER_ID)).isNull();
        assertThat(recebida.getHeader(CabecalhosDeIdentidade.USER_ROLE)).isNull();
    }

    @Test
    @DisplayName("identidade forjada e substituida pela do token, e nao somada a ela")
    void deveSobreporIdentidadeForjadaPelaDoToken() throws InterruptedException {
        UUID verdadeiro = UUID.randomUUID();
        UUID forjado = UUID.randomUUID();

        cliente.get().uri("/api/events")
                .header(HttpHeaders.AUTHORIZATION,
                        "Bearer " + GeradorDeToken.valido(verdadeiro, "joao@teste.com", Role.USER))
                .header(CabecalhosDeIdentidade.USER_ID, forjado.toString())
                .header(CabecalhosDeIdentidade.USER_ROLE, "ADMIN")
                .exchange().expectStatus().isOk();

        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        // Um unico valor por cabecalho: dois X-User-Role fariam o servico ler o primeiro, que
        // poderia ser o do atacante.
        assertThat(recebida.getHeaders().values(CabecalhosDeIdentidade.USER_ID))
                .containsExactly(verdadeiro.toString());
        assertThat(recebida.getHeaders().values(CabecalhosDeIdentidade.USER_ROLE))
                .containsExactly("USER");
    }

    @Test
    @DisplayName("requisicao sem token passa: quem exige identificacao e o servico")
    void devePermitirRequisicaoAnonima() throws InterruptedException {
        cliente.get().uri("/api/events").exchange().expectStatus().isOk();

        // O gateway autentica, mas nao autoriza. Replicar aqui a lista de rotas publicas criaria
        // uma segunda copia das regras de acesso, e duas copias divergem.
        RecordedRequest recebida = proximaRequisicao(EVENT);
        assertThat(recebida).isNotNull();
        assertThat(recebida.getHeader(CabecalhosDeIdentidade.USER_ID)).isNull();
    }

    // ---------- token invalido ----------

    @Test
    @DisplayName("token expirado para no gateway com 401")
    void deveRecusarTokenExpirado() throws InterruptedException {
        verificarRecusa(GeradorDeToken.expirado(UUID.randomUUID()));
    }

    @Test
    @DisplayName("token assinado com outra chave para no gateway com 401")
    void deveRecusarTokenForjado() throws InterruptedException {
        verificarRecusa(GeradorDeToken.assinadoPorOutraChave(UUID.randomUUID()));
    }

    @Test
    @DisplayName("token de outro emissor para no gateway com 401")
    void deveRecusarTokenDeOutroEmissor() throws InterruptedException {
        verificarRecusa(GeradorDeToken.deOutroEmissor(UUID.randomUUID()));
    }

    @Test
    @DisplayName("texto qualquer no lugar do token para no gateway com 401")
    void deveRecusarTokenIlegivel() throws InterruptedException {
        verificarRecusa("isto-nao-e-um-jwt");
    }

    private void verificarRecusa(String token) throws InterruptedException {
        cliente.get().uri("/api/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                // Mesmo formato de erro dos servicos: o frontend trata um so.
                .expectBody()
                .jsonPath("$.error").isEqualTo("INVALID_TOKEN")
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.path").isEqualTo("/api/events")
                .jsonPath("$.traceId").isNotEmpty();

        // Nenhum servico chega a ser chamado: um token invalido nao vira valido adiante, e
        // encaminhar so gastaria uma ida a rede para receber a mesma resposta.
        assertThat(proximaRequisicao(EVENT)).isNull();
    }

    // ---------- CORS ----------

    @Test
    @DisplayName("preflight e respondido pelo gateway, sem incomodar o servico")
    void deveResponderOPreflight() throws InterruptedException {
        cliente.options().uri("/api/bookings")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,idempotency-key")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");

        assertThat(proximaRequisicao(BOOKING)).isNull();
    }

    @Test
    @DisplayName("origem nao autorizada e recusada")
    void deveRecusarOrigemDesconhecida() {
        cliente.options().uri("/api/bookings")
                .header(HttpHeaders.ORIGIN, "http://site-malicioso.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }
}
