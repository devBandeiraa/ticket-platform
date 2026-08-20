package com.devbandeiraa.bookingservice.client;

import com.devbandeiraa.bookingservice.exception.EventServiceIndisponivelException;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Le do event-service os dados de um evento.
 *
 * <p>Usa o endpoint publico {@code GET /events/{id}} em vez do {@code /internal/events/{id}} que
 * o mapeamento previa. O endpoint interno nao se justificou: o publico ja devolve capacidade e
 * preco, e ja filtra por eventos publicados — que e exatamente a regra desejada, porque nao se
 * vende ingresso de rascunho. Um endpoint a mais entregaria os mesmos dados sob outra URL, com
 * mais superficie para manter em sincronia.
 *
 * <p>Chamado uma unica vez por evento, na hidratacao do estoque local. Depois disso as reservas
 * daquele evento nao dependem mais do event-service estar no ar.
 */
@Component
public class EventClient {

    private static final Logger log = LoggerFactory.getLogger(EventClient.class);

    private final RestClient restClient;

    public EventClient(RestClient eventRestClient) {
        this.restClient = eventRestClient;
    }

    /**
     * Busca um evento publicado.
     *
     * @throws EventoNaoDisponivelException      se o evento nao existe ou nao esta publicado
     * @throws EventServiceIndisponivelException se o event-service nao respondeu
     */
    public EventSnapshot buscarPublicado(UUID eventId) {
        try {
            EventSnapshot evento = restClient.get()
                    .uri("/events/{id}", eventId)
                    .retrieve()
                    .body(EventSnapshot.class);

            if (evento == null) {
                throw new EventoNaoDisponivelException(eventId);
            }

            log.debug("evento {} obtido do event-service: capacidade={} preco={}",
                    eventId, evento.totalTickets(), evento.price());

            return evento;

        } catch (HttpClientErrorException.NotFound naoEncontrado) {
            // O event-service devolve 404 tanto para evento inexistente quanto para rascunho ou
            // cancelado. Do ponto de vista de quem reserva, os tres casos sao o mesmo: nao ha o
            // que comprar. Distingui-los aqui revelaria a existencia de eventos nao publicados.
            throw new EventoNaoDisponivelException(eventId);

        } catch (RestClientException falhaDeComunicacao) {
            // Timeout, conexao recusada, 5xx. Diferente do 404, aqui nao se sabe se o evento
            // existe — e responder "nao existe" quando a verdade e "nao consegui perguntar"
            // levaria o usuario a desistir de um evento que esta a venda.
            throw new EventServiceIndisponivelException(eventId, falhaDeComunicacao);
        }
    }
}
