package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * O event-service nao respondeu a tempo, ou nao respondeu.
 *
 * <p>Traduzida em {@code 503 EVENT_SERVICE_UNAVAILABLE}, e nao em {@code 404}: nao se sabe se o
 * evento existe, apenas que nao foi possivel perguntar. Responder "nao existe" faria o usuario
 * desistir de um evento que talvez esteja a venda.
 *
 * <p>Vale notar que isto so pode acontecer na <em>primeira</em> reserva de cada evento, quando o
 * estoque local ainda nao foi hidratado. Depois disso o event-service pode cair sem afetar a
 * venda — que e justamente o ponto de manter o contador local.
 */
public class EventServiceIndisponivelException extends RuntimeException {

    public EventServiceIndisponivelException(UUID eventId, Throwable causa) {
        super("nao foi possivel consultar o evento %s no event-service".formatted(eventId), causa);
    }
}
