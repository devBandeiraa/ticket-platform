package com.devbandeiraa.bookingservice.dto.response;

import com.devbandeiraa.bookingservice.domain.EventInventory;
import java.util.UUID;

/**
 * Disponibilidade de um evento.
 *
 * <p>E deliberadamente uma foto, e nao uma promessa: entre ler este numero e concluir a reserva,
 * outra pessoa pode ter levado o ultimo ingresso. O contrato do sistema e que a reserva devolve
 * {@code 409 SOLD_OUT} quando isso acontece — este endpoint serve para a tela, nao para a
 * decisao.
 *
 * <p>Vive no booking-service, e nao no event-service, porque quem sabe quanto ja foi reservado e
 * quem guarda as reservas.
 */
public record AvailabilityResponse(UUID eventId, int total, int reserved, int available) {

    public static AvailabilityResponse de(EventInventory estoque) {
        return new AvailabilityResponse(
                estoque.getEventId(),
                estoque.getTotalTickets(),
                estoque.getReservedTickets(),
                estoque.getDisponivel());
    }
}
