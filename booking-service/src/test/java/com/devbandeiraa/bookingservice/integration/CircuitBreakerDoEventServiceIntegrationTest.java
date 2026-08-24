package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.exception.EventServiceIndisponivelException;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * O circuit breaker da chamada ao event-service.
 *
 * <p>Os limiares sao reduzidos aqui para o teste ser curto. Como isto muda a configuracao, o Spring
 * monta um contexto proprio para esta classe — o que tambem garante que o estado do circuito nao
 * escape para os demais testes de integracao, que dependem da hidratacao de estoque funcionando.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "CB_EVENT_JANELA=4",
        "CB_EVENT_MINIMO=4",
        "CB_EVENT_LIMIAR=50",
        // Longa de proposito: nenhum teste daqui verifica a recuperacao, e uma espera curta faria
        // o circuito voltar sozinho para meio-aberto no meio de outra verificacao.
        "CB_EVENT_ESPERA=60s"
})
class CircuitBreakerDoEventServiceIntegrationTest {

    private static final MockWebServer EVENT_SERVICE = iniciar();

    @Autowired
    private EventClient eventClient;

    @Autowired
    private CircuitBreakerRegistry registro;

    @DynamicPropertySource
    static void apontarParaOEventServiceDeMentira(DynamicPropertyRegistry registro) {
        registro.add("booking.event-service.url", () -> EVENT_SERVICE.url("/").toString());
    }

    @BeforeEach
    @AfterEach
    void zerarOCircuito() {
        // Antes e depois. O circuito e um bean de aplicacao e guarda estado entre testes: sem o
        // reset, a ordem de execucao passaria a importar, e um teste que abre o circuito faria o
        // seguinte falhar por um motivo que nao tem nada a ver com o que ele verifica.
        circuito().reset();
    }

    @Test
    @DisplayName("falhas seguidas abrem o circuito, e a chamada seguinte nem sai")
    void deveAbrirOCircuitoAposFalhas() {
        for (int chamada = 0; chamada < 4; chamada++) {
            EVENT_SERVICE.enqueue(new MockResponse().setResponseCode(500));
            assertThatThrownBy(() -> eventClient.buscarPublicado(UUID.randomUUID()))
                    .isInstanceOf(EventServiceIndisponivelException.class);
        }

        assertThat(circuito().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int requisicoesAteAqui = EVENT_SERVICE.getRequestCount();

        // Nenhuma resposta enfileirada de proposito: se a chamada saisse, ela ficaria pendurada
        // ate o timeout — e e exatamente esse tempo que o circuito aberto existe para poupar.
        assertThatThrownBy(() -> eventClient.buscarPublicado(UUID.randomUUID()))
                .isInstanceOf(EventServiceIndisponivelException.class);

        assertThat(EVENT_SERVICE.getRequestCount())
                .as("com o circuito aberto, a chamada falha sem tocar a rede")
                .isEqualTo(requisicoesAteAqui);
    }

    @Test
    @DisplayName("evento inexistente nao abre o circuito")
    void naoDeveAbrirOCircuitoCom404() {
        // A configuracao mais importante desta fase. Um 404 significa que o event-service esta
        // saudavel e respondeu depressa; conta-lo como falha faria um punhado de usuarios
        // digitando ids errados derrubar a hidratacao de todos os eventos legitimos.
        for (int chamada = 0; chamada < 6; chamada++) {
            EVENT_SERVICE.enqueue(new MockResponse().setResponseCode(404));
            assertThatThrownBy(() -> eventClient.buscarPublicado(UUID.randomUUID()))
                    .isInstanceOf(EventoNaoDisponivelException.class);
        }

        assertThat(circuito().getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // E segue chamando de verdade: o 404 nao consumiu credito nenhum.
        int requisicoesAteAqui = EVENT_SERVICE.getRequestCount();
        EVENT_SERVICE.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> eventClient.buscarPublicado(UUID.randomUUID()))
                .isInstanceOf(EventoNaoDisponivelException.class);

        assertThat(EVENT_SERVICE.getRequestCount()).isEqualTo(requisicoesAteAqui + 1);
    }

    private CircuitBreaker circuito() {
        return registro.circuitBreaker(EventClient.INSTANCIA);
    }

    private static MockWebServer iniciar() {
        MockWebServer servidor = new MockWebServer();
        try {
            servidor.start();
        } catch (IOException falha) {
            throw new UncheckedIOException("nao foi possivel subir o event-service de mentira", falha);
        }
        return servidor;
    }
}
