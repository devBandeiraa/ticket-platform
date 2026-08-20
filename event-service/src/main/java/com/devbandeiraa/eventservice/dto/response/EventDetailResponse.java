package com.devbandeiraa.eventservice.dto.response;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento completo.
 *
 * <p>Note que nao ha campo de "ingressos disponiveis": este servico conhece a capacidade, nao o
 * quanto ja foi vendido. Essa contagem pertence ao booking-service, e o frontend a busca de la
 * ao montar a tela de detalhe. Inventar o campo aqui exigiria uma chamada sincrona a cada
 * listagem e devolveria um numero desatualizado no instante seguinte.
 */
public record EventDetailResponse(
        UUID id,
        String name,
        String description,
        String venue,
        Instant eventDate,
        BigDecimal price,
        int totalTickets,
        EventStatus status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static EventDetailResponse de(Event evento) {
        return new EventDetailResponse(
                evento.getId(),
                evento.getName(),
                evento.getDescription(),
                evento.getVenue(),
                evento.getEventDate(),
                evento.getPrice(),
                evento.getTotalTickets(),
                evento.getStatus(),
                evento.getCreatedBy(),
                evento.getCreatedAt(),
                evento.getUpdatedAt());
    }
}
