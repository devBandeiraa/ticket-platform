package com.devbandeiraa.apigateway.integration;

import static com.devbandeiraa.apigateway.support.PrometheusDeMentira.serie;
import static com.devbandeiraa.apigateway.support.PrometheusDeMentira.vetor;

import com.devbandeiraa.apigateway.support.PrometheusDeMentira;
import com.devbandeiraa.apigateway.support.ServicosDeMentira;
import com.devbandeiraa.apigateway.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * O endpoint que alimenta a pagina {@code /status}.
 *
 * <p>Ele e o unico caminho que o gateway responde por conta propria, e isso muda o que precisa ser
 * verificado. Nao ha rota casando com {@code /api/status}, entao nem o {@code StripPrefix} nem os
 * filtros globais rodam — e o CORS, que para as rotas vem do {@code globalcors} do Spring Cloud
 * Gateway, aqui precisa vir de outro lugar. Cada uma dessas afirmacoes tem um teste abaixo, porque
 * todas falham em silencio: o navegador so mostraria "servidor fora do ar".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfig.class)
class PainelDeStatusIntegrationTest {

    private static final String CAMINHO = "/api/status";
    private static final String ORIGEM_DO_FRONTEND = "http://localhost:5173";

    @Autowired
    private WebTestClient cliente;

    @DynamicPropertySource
    static void apontarParaOsDubles(DynamicPropertyRegistry registro) {
        ServicosDeMentira.registrarEnderecos(registro);
        PrometheusDeMentira.registrarEndereco(registro);
    }

    @Test
    @DisplayName("traduz as series do Prometheus no retrato que a tela consome")
    void deveTraduzirAsSeries() {
        PrometheusDeMentira.responder(
                vetor(serie("job", "api-gateway", "1"), serie("job", "booking-service", "1")),
                // Segundos, como o Prometheus mede. A tela recebe milissegundos.
                vetor(serie("job", "api-gateway", "0.0125"),
                        serie("job", "booking-service", "0.042")),
                vetor(serie("job", "api-gateway", "3601.5"),
                        serie("job", "booking-service", "900.2")),
                vetor());

        cliente.get().uri(CAMINHO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                // Ordem alfabetica e estavel: a pagina se atualiza sozinha, e cartoes trocando de
                // lugar a cada coleta tornariam a tela ilegivel.
                .jsonPath("$.servicos[0].nome").isEqualTo("api-gateway")
                .jsonPath("$.servicos[0].noAr").isEqualTo(true)
                .jsonPath("$.servicos[0].latenciaMediaMs").isEqualTo(12.5)
                // Truncado, e nao arredondado: "3601 segundos no ar" nao precisa de casas decimais.
                .jsonPath("$.servicos[0].uptimeSegundos").isEqualTo(3601)
                .jsonPath("$.servicos[1].nome").isEqualTo("booking-service")
                .jsonPath("$.servicos[1].latenciaMediaMs").isEqualTo(42.0);
    }

    @Test
    @DisplayName("servico que nao respondeu a coleta aparece fora do ar")
    void deveMarcarServicoFora() {
        // `up` valendo zero e o Prometheus dizendo que a coleta falhou. Nao e o servico se
        // declarando doente — e um terceiro constatando que ele nao respondeu, que e a unica
        // forma de saber que um processo morreu de vez.
        PrometheusDeMentira.responder(
                vetor(serie("job", "event-service", "0"), serie("job", "api-gateway", "1")),
                vetor(),
                vetor(serie("job", "api-gateway", "120")),
                vetor());

        cliente.get().uri(CAMINHO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.servicos[1].nome").isEqualTo("event-service")
                .jsonPath("$.servicos[1].noAr").isEqualTo(false)
                // Sem processo no ar nao ha de quem perguntar ha quanto tempo ele subiu. Nulo, e
                // nao zero: zero significaria "acabou de subir", que e o oposto.
                .jsonPath("$.servicos[1].uptimeSegundos").doesNotExist();
    }

    @Test
    @DisplayName("sem trafego na janela, a latencia e nula e nao NaN")
    void deveTratarDivisaoPorZero() {
        // O Prometheus responde "NaN" literalmente quando a taxa do denominador e zero — o caso de
        // um servico ocioso. Repassado como numero, a tela mostraria "NaN ms"; convertido para
        // zero, mentiria dizendo que as respostas sao instantaneas.
        PrometheusDeMentira.responder(
                vetor(serie("job", "notification-service", "1")),
                vetor(serie("job", "notification-service", "NaN")),
                vetor(serie("job", "notification-service", "60")),
                vetor());

        cliente.get().uri(CAMINHO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.servicos[0].noAr").isEqualTo(true)
                .jsonPath("$.servicos[0].latenciaMediaMs").doesNotExist();
    }

    @Test
    @DisplayName("o estado do circuito chega traduzido, e nao como numero")
    void deveTraduzirOEstadoDoCircuito() {
        // 2 e o resultado de `aberto * 2 + meio_aberto` — a forma como a consulta colapsa quatro
        // series binarias numa escala. A tela nao deveria precisar conhecer essa aritmetica.
        PrometheusDeMentira.responder(
                vetor(serie("job", "booking-service", "1")),
                vetor(),
                vetor(serie("job", "booking-service", "10")),
                vetor(serie("name", "event-service", "2")));

        cliente.get().uri(CAMINHO)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.circuitos[0].nome").isEqualTo("event-service")
                .jsonPath("$.circuitos[0].estado").isEqualTo("ABERTO");
    }

    @Test
    @DisplayName("com a fonte de metricas fora, responde 503 em vez de pintar tudo de vermelho")
    void deveDistinguirFonteForaDePlataformaFora() {
        PrometheusDeMentira.falhar();

        cliente.get().uri(CAMINHO)
                .exchange()
                // A distincao e o ponto. Devolver o retrato com todos os servicos marcados como
                // fora seria mentir com precisao: mandaria alguem investigar seis servicos
                // saudaveis enquanto o que caiu foi o Prometheus.
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.error").isEqualTo("METRICS_UNAVAILABLE")
                .jsonPath("$.path").isEqualTo(CAMINHO);
    }

    @Test
    @DisplayName("o preflight do navegador e respondido para a origem do frontend")
    void deveResponderAoPreflight() {
        // Este caminho nao e uma rota, e o `globalcors` do Spring Cloud Gateway configura o
        // roteamento — nao o controller. Sem CORS proprio aqui, a pagina falharia no preflight e
        // reportaria "servidor fora do ar" com a plataforma inteira no ar.
        cliente.method(HttpMethod.OPTIONS).uri(CAMINHO)
                .header(HttpHeaders.ORIGIN, ORIGEM_DO_FRONTEND)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGEM_DO_FRONTEND);
    }
}
