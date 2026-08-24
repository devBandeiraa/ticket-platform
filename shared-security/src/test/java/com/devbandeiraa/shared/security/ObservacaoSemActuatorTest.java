package com.devbandeiraa.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * O filtro que separa o trafego de verdade do proprio monitoramento.
 *
 * <p>Vale um teste porque a falha e silenciosa nos dois sentidos. Filtrando de menos, o Jaeger volta
 * a afogar as requisicoes reais em healthcheck — e ninguem percebe ate precisar investigar algo.
 * Filtrando de mais, uma rota legitima some da observabilidade sem nenhum erro: os graficos apenas
 * passam a mostrar menos do que acontece.
 */
class ObservacaoSemActuatorTest {

    private final ObservacaoSemActuator filtro = new ObservacaoSemActuator();

    @ParameterizedTest
    @ValueSource(strings = {
            "/actuator/health",
            "/actuator/prometheus",
            "/actuator/circuitbreakers",
            "/actuator"})
    @DisplayName("descarta as requisicoes do proprio monitoramento")
    void deveDescartarActuator(String caminho) {
        assertThat(filtro.test("http.server.requests", contextoDe(caminho))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/bookings",
            "/events/123/availability",
            "/auth/login",
            "/payments"})
    @DisplayName("deixa passar o trafego de verdade")
    void deveManterTrafegoReal(String caminho) {
        assertThat(filtro.test("http.server.requests", contextoDe(caminho))).isTrue();
    }

    @Test
    @DisplayName("um caminho que apenas contem 'actuator' no meio continua observado")
    void deveCompararPeloPrefixo() {
        // Nao e preciosismo: um `contains` no lugar do `startsWith` apagaria da observabilidade
        // qualquer rota que por acaso tivesse a palavra, e o defeito so apareceria como um grafico
        // mais baixo do que deveria.
        assertThat(filtro.test("http.server.requests", contextoDe("/events/actuator-teste")))
                .isTrue();
    }

    @Test
    @DisplayName("observacao que nao e requisicao HTTP passa intacta")
    void deveIgnorarOutrosContextos() {
        // O lock distribuido, a publicacao na outbox e o consumo de mensagem produzem observacoes
        // sem requisicao nenhuma. Descarta-las por engano tiraria do Jaeger justamente a parte
        // assincrona, que e a mais dificil de acompanhar sem trace.
        assertThat(filtro.test("booking.lock", new Observation.Context())).isTrue();
    }

    private ServerRequestObservationContext contextoDe(String caminho) {
        return new ServerRequestObservationContext(
                new MockHttpServletRequest("GET", caminho), new MockHttpServletResponse());
    }
}
