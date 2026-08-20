package com.devbandeiraa.bookingservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.devbandeiraa.bookingservice.client.EventClient;
import com.devbandeiraa.bookingservice.client.EventSnapshot;
import com.devbandeiraa.bookingservice.exception.EventServiceIndisponivelException;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Testes do cliente do event-service, sem servidor real.
 *
 * <p>O que se verifica aqui e a traducao das respostas HTTP em excecoes de dominio. A distincao
 * entre 404 e falha de comunicacao importa: uma diz "nao ha o que reservar", a outra diz "nao
 * consegui perguntar", e confundi-las levaria o usuario a desistir de um evento a venda.
 */
class EventClientTest {

    private static final String BASE = "http://event-service:8082";

    private MockRestServiceServer servidorSimulado;
    private EventClient eventClient;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        servidorSimulado = MockRestServiceServer.bindTo(builder).build();
        eventClient = new EventClient(builder.build());
    }

    @Test
    @DisplayName("le capacidade e preco do evento publicado")
    void deveLerCapacidadeEPreco() {
        UUID eventoId = UUID.randomUUID();
        servidorSimulado.expect(requestTo(BASE + "/events/" + eventoId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id": "%s",
                          "name": "Show de Rock",
                          "venue": "Estadio Municipal",
                          "totalTickets": 500,
                          "price": 150.00,
                          "status": "PUBLISHED"
                        }
                        """.formatted(eventoId), MediaType.APPLICATION_JSON));

        EventSnapshot evento = eventClient.buscarPublicado(eventoId);

        assertThat(evento.id()).isEqualTo(eventoId);
        assertThat(evento.totalTickets()).isEqualTo(500);
        assertThat(evento.price()).isEqualByComparingTo(new BigDecimal("150.00"));
        servidorSimulado.verify();
    }

    @Test
    @DisplayName("campos que o booking-service nao usa sao simplesmente ignorados")
    void deveIgnorarCamposDesconhecidos() {
        UUID eventoId = UUID.randomUUID();
        servidorSimulado.expect(requestTo(BASE + "/events/" + eventoId))
                .andRespond(withSuccess("""
                        {
                          "id": "%s",
                          "totalTickets": 10,
                          "price": 50.00,
                          "campoQueAindaNaoExiste": "valor futuro"
                        }
                        """.formatted(eventoId), MediaType.APPLICATION_JSON));

        // O event-service pode acrescentar campos sem quebrar este cliente.
        assertThat(eventClient.buscarPublicado(eventoId).totalTickets()).isEqualTo(10);
    }

    @Test
    @DisplayName("404 vira evento indisponivel, e nao erro de servidor")
    void deveTraduzir404() {
        UUID eventoId = UUID.randomUUID();
        servidorSimulado.expect(requestTo(BASE + "/events/" + eventoId))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> eventClient.buscarPublicado(eventoId))
                .isInstanceOf(EventoNaoDisponivelException.class);
    }

    @Test
    @DisplayName("falha do event-service nao e confundida com evento inexistente")
    void deveTraduzirFalhaDeComunicacao() {
        UUID eventoId = UUID.randomUUID();
        servidorSimulado.expect(requestTo(BASE + "/events/" + eventoId))
                .andRespond(withServerError());

        // Responder "nao existe" aqui seria mentir: nao se sabe se o evento existe.
        assertThatThrownBy(() -> eventClient.buscarPublicado(eventoId))
                .isInstanceOf(EventServiceIndisponivelException.class);
    }
}
