package com.devbandeiraa.apigateway.integration;

import static com.devbandeiraa.apigateway.support.ServicosDeMentira.EVENT;
import static com.devbandeiraa.apigateway.support.ServicosDeMentira.proximaRequisicao;
import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.apigateway.support.GeradorDeToken;
import com.devbandeiraa.apigateway.support.ServicosDeMentira;
import com.devbandeiraa.apigateway.support.TestcontainersConfig;
import com.devbandeiraa.shared.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Politica de limite de requisicoes.
 *
 * <p>A <em>forma</em> da politica e a de producao — um balde geral valendo para todas as rotas e um
 * balde estreito somado a ele no login —, so os numeros mudam, para o teste nao precisar disparar
 * quarenta requisicoes.
 *
 * <p>O ajuste e feito no custo de cada requisicao, e nao na capacidade do balde, e a razao e evitar
 * teste intermitente. O script do rate limiter mede o tempo em segundos inteiros, entao um teste
 * que leve mais de um segundo ganha fichas de volta no meio do caminho. Com capacidade 100 e custo
 * 20, cabem cinco requisicoes e a sexta e recusada; a uma ou duas fichas que a reposicao devolva
 * durante a execucao faltaria muito para pagar uma sexta, e o resultado nao depende de a maquina
 * estar mais rapida naquele dia.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
        "RATE_LIMIT_POR_SEGUNDO=1",
        "RATE_LIMIT_RAJADA=100",
        "RATE_LIMIT_CUSTO=20",
        "RATE_LIMIT_LOGIN_REPOSICAO=1",
        "RATE_LIMIT_LOGIN_RAJADA=40",
        "RATE_LIMIT_LOGIN_CUSTO=20"
})
class RateLimitIntegrationTest {

    /** 100 fichas a 20 por requisicao. */
    private static final int REQUISICOES_ATE_O_LIMITE = 5;

    /** 40 fichas a 20 por tentativa. */
    private static final int LOGINS_ATE_O_LIMITE = 2;

    @Autowired
    private WebTestClient cliente;

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @DynamicPropertySource
    static void apontarParaOsServicosDeMentira(DynamicPropertyRegistry registro) {
        ServicosDeMentira.registrarEnderecos(registro);
    }

    @BeforeEach
    void zerarBaldes() throws InterruptedException {
        // Sem isto, o balde gasto por um teste faria o seguinte comecar sem fichas.
        ReactiveRedisConnection conexao = redis.getConnectionFactory().getReactiveConnection();
        try {
            conexao.serverCommands().flushAll().block();
        } finally {
            conexao.close();
        }
        ServicosDeMentira.limpar();
    }

    @Test
    @DisplayName("estourado o balde, a requisicao seguinte recebe 429")
    void deveRecusarAcimaDoLimite() {
        gastarOBaldeGeral();

        cliente.get().uri("/api/events").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("o 429 sai no mesmo formato de erro do resto da API")
    void deveResponderComCorpoDeErro() {
        gastarOBaldeGeral();

        // Sem o filtro que da corpo ao 429, o rate limiter encerraria a resposta vazia e o
        // frontend receberia um corpo em branco justamente onde precisa explicar algo ao usuario.
        cliente.get().uri("/api/events").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody()
                .jsonPath("$.error").isEqualTo("RATE_LIMIT_EXCEEDED")
                .jsonPath("$.status").isEqualTo(429)
                .jsonPath("$.path").isEqualTo("/api/events")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.traceId").isNotEmpty();
    }

    @Test
    @DisplayName("recusada pelo limite, a requisicao nao chega ao servico")
    void naoDeveEncaminharRequisicaoRecusada() throws InterruptedException {
        gastarOBaldeGeral();
        ServicosDeMentira.limpar();

        cliente.get().uri("/api/events").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // O ponto do rate limiting: proteger o que esta atras, e nao apenas responder 429 depois
        // de ja ter repassado a carga adiante.
        assertThat(proximaRequisicao(EVENT)).isNull();
    }

    @Test
    @DisplayName("o login tem balde proprio, mais estreito que o geral")
    void deveLimitarOLoginAntesDoLimiteGeral() {
        for (int tentativa = 0; tentativa < LOGINS_ATE_O_LIMITE; tentativa++) {
            cliente.post().uri("/api/auth/login").exchange().expectStatus().isOk();
        }

        // Duas tentativas contra um balde geral que comporta cinco: se o login nao tivesse balde
        // proprio, ainda haveria fichas de sobra e esta terceira passaria.
        cliente.post().uri("/api/auth/login").exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("o balde do login nao derruba as demais rotas")
    void naoDeveContaminarAsDemaisRotas() {
        for (int tentativa = 0; tentativa <= LOGINS_ATE_O_LIMITE; tentativa++) {
            cliente.post().uri("/api/auth/login").exchange();
        }

        // Sao baldes independentes: esgotar as tentativas de senha nao pode impedir o visitante
        // de continuar navegando pelo catalogo.
        cliente.get().uri("/api/events").exchange().expectStatus().isOk();
    }

    @Test
    @DisplayName("usuarios autenticados diferentes nao dividem o mesmo balde")
    void deveSepararOsBaldesPorUsuario() {
        String primeiro = "Bearer " + GeradorDeToken.valido(
                UUID.randomUUID(), "ana@teste.com", Role.USER);
        String segundo = "Bearer " + GeradorDeToken.valido(
                UUID.randomUUID(), "bruno@teste.com", Role.USER);

        for (int requisicao = 0; requisicao < REQUISICOES_ATE_O_LIMITE; requisicao++) {
            cliente.get().uri("/api/events")
                    .header(HttpHeaders.AUTHORIZATION, primeiro)
                    .exchange().expectStatus().isOk();
        }

        cliente.get().uri("/api/events")
                .header(HttpHeaders.AUTHORIZATION, primeiro)
                .exchange().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Os dois chegam do mesmo endereco, 127.0.0.1. Contando por IP, o segundo pagaria pelo
        // consumo do primeiro — que e o que aconteceria com todo mundo atras de um mesmo NAT.
        cliente.get().uri("/api/events")
                .header(HttpHeaders.AUTHORIZATION, segundo)
                .exchange().expectStatus().isOk();
    }

    private void gastarOBaldeGeral() {
        for (int requisicao = 0; requisicao < REQUISICOES_ATE_O_LIMITE; requisicao++) {
            cliente.get().uri("/api/events").exchange().expectStatus().isOk();
        }
    }
}
