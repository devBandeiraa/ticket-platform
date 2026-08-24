package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.bookingservice.client.Autorizacao;
import com.devbandeiraa.bookingservice.client.PagamentoClient;
import com.devbandeiraa.bookingservice.exception.PagamentoIndisponivelException;
import com.devbandeiraa.bookingservice.exception.PagamentoRecusadoException;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O retry da chamada ao provedor de pagamento.
 *
 * <p>Servidor HTTP de mentira e nao um dublê do cliente, porque o que se verifica aqui nao e o
 * valor devolvido: e <em>quantas</em> requisicoes chegaram do outro lado e com quais cabecalhos.
 * Um mock de objeto nao mostraria nem uma coisa nem outra.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class RetryDePagamentoIntegrationTest {

    private static final MockWebServer PROVEDOR = iniciar();

    @Autowired
    private PagamentoClient pagamentoClient;

    /**
     * Marco zero do teste corrente.
     *
     * <p>O {@code getRequestCount()} do MockWebServer conta desde que o servidor subiu e nao volta
     * a zero: esvaziar a fila de requisicoes gravadas nao o reinicia, porque sao coisas distintas.
     * Como o servidor e estatico — precisa existir antes do {@code @DynamicPropertySource} —, ele e
     * o mesmo para os quatro testes, e comparar o total contra um numero fixo daria um resultado
     * dependente da ordem em que o JUnit resolveu executa-los.
     */
    private int requisicoesAntesDoTeste;

    @DynamicPropertySource
    static void apontarParaOProvedorDeMentira(DynamicPropertyRegistry registro) {
        registro.add("booking.pagamento.url", () -> PROVEDOR.url("/").toString());
    }

    @BeforeEach
    void limparTrafegoAnterior() throws InterruptedException {
        while (PROVEDOR.takeRequest(50, TimeUnit.MILLISECONDS) != null) {
            // apenas esvazia a fila do teste anterior
        }
        requisicoesAntesDoTeste = PROVEDOR.getRequestCount();
    }

    /** Quantas requisicoes chegaram ao provedor durante este teste, e nao desde que ele subiu. */
    private int requisicoesRecebidas() {
        return PROVEDOR.getRequestCount() - requisicoesAntesDoTeste;
    }

    @Test
    @DisplayName("falha transitoria e repetida ate passar")
    void deveRepetirAteObterAutorizacao() throws InterruptedException {
        PROVEDOR.enqueue(new MockResponse().setResponseCode(503));
        PROVEDOR.enqueue(new MockResponse().setResponseCode(503));
        PROVEDOR.enqueue(autorizacao("AUT-ABC123"));

        Autorizacao autorizacao =
                pagamentoClient.autorizar(UUID.randomUUID(), new BigDecimal("150.00"));

        assertThat(autorizacao.authorizationCode()).isEqualTo("AUT-ABC123");
        // Tres idas ao provedor: a original e duas repeticoes. Uma venda que teria sido perdida
        // na primeira tentativa.
        assertThat(requisicoesRecebidas()).isEqualTo(3);
    }

    @Test
    @DisplayName("as tentativas usam sempre a mesma chave de idempotencia")
    void deveManterAChaveEntreAsTentativas() throws InterruptedException {
        UUID reserva = UUID.randomUUID();

        PROVEDOR.enqueue(new MockResponse().setResponseCode(503));
        PROVEDOR.enqueue(autorizacao("AUT-ABC123"));

        pagamentoClient.autorizar(reserva, new BigDecimal("150.00"));

        RecordedRequest primeira = PROVEDOR.takeRequest(1, TimeUnit.SECONDS);
        RecordedRequest segunda = PROVEDOR.takeRequest(1, TimeUnit.SECONDS);

        assertThat(primeira).isNotNull();
        assertThat(segunda).isNotNull();

        // Este e o teste mais importante da classe. Sem a chave estavel, a segunda tentativa seria
        // uma cobranca nova, e o retry — que parece boa pratica — cobraria duas vezes de uma
        // pessoa real. A chave deriva do id da reserva justamente para nao variar.
        assertThat(primeira.getHeader("Idempotency-Key"))
                .isEqualTo("booking-" + reserva)
                .isEqualTo(segunda.getHeader("Idempotency-Key"));
    }

    @Test
    @DisplayName("recusa definitiva nao e repetida")
    void naoDeveRepetirRecusa() {
        PROVEDOR.enqueue(new MockResponse().setResponseCode(402)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"code\":\"PAYMENT_DECLINED\",\"message\":\"fundos insuficientes\"}"));

        assertThatThrownBy(() ->
                pagamentoClient.autorizar(UUID.randomUUID(), new BigDecimal("150.00")))
                .isInstanceOf(PagamentoRecusadoException.class);

        // Uma unica ida. Repetir uma recusa gastaria quatro tentativas e cerca de um segundo e
        // meio de espera para chegar exatamente a mesma resposta, com o usuario olhando a tela.
        assertThat(requisicoesRecebidas()).isEqualTo(1);
    }

    @Test
    @DisplayName("esgotadas as tentativas, a falha sobe para o chamador")
    void deveDesistirAposOLimite() {
        for (int tentativa = 0; tentativa < 4; tentativa++) {
            PROVEDOR.enqueue(new MockResponse().setResponseCode(503));
        }

        assertThatThrownBy(() ->
                pagamentoClient.autorizar(UUID.randomUUID(), new BigDecimal("150.00")))
                .isInstanceOf(PagamentoIndisponivelException.class);

        // Quatro no total, conforme max-attempts. Insistir alem disso empilharia requisicoes sobre
        // um provedor que ja demonstrou nao estar respondendo.
        assertThat(requisicoesRecebidas()).isEqualTo(4);
    }

    private MockResponse autorizacao(String comprovante) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"bookingId":"%s","authorizationCode":"%s","repetida":false}
                        """.formatted(UUID.randomUUID(), comprovante));
    }

    /**
     * Iniciado em bloco estatico: o endereco precisa existir quando o
     * {@code @DynamicPropertySource} monta a configuracao, o que acontece antes de qualquer
     * {@code @BeforeAll}.
     */
    private static MockWebServer iniciar() {
        MockWebServer servidor = new MockWebServer();
        try {
            servidor.start();
        } catch (IOException falha) {
            throw new UncheckedIOException("nao foi possivel subir o provedor de mentira", falha);
        }
        return servidor;
    }
}
