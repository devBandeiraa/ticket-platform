package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * O evento nao existe, ou existe mas nao esta a venda.
 *
 * <p>Traduzida em {@code 404 EVENT_NOT_AVAILABLE}. Os dois casos compartilham a resposta de
 * proposito: distingui-los revelaria a existencia de eventos ainda em rascunho.
 */
public class EventoNaoDisponivelException extends RuntimeException {

    public EventoNaoDisponivelException(UUID eventId) {
        super("evento %s nao esta disponivel para reserva".formatted(eventId));
    }
}
