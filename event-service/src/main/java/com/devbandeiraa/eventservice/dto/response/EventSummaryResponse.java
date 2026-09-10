package com.devbandeiraa.eventservice.dto.response;

import com.devbandeiraa.eventservice.domain.Event;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Versao enxuta usada na listagem.
 *
 * <p>Sem descricao de proposito: uma pagina de 20 eventos carregaria 20 textos longos que a tela
 * de listagem nao mostra. Quem abre o detalhe recebe o {@link EventDetailResponse} completo.
 *
 * <p>A capa segue o mesmo criterio e por isso entra: o cartao do catalogo a exibe. O corte e por
 * o que a listagem desenha, e nao pelo tamanho do campo.
 */
public record EventSummaryResponse(
        UUID id,
        String name,
        String venue,
        Instant eventDate,
        BigDecimal price,
        int totalTickets,
        String imageUrl) {

    public static EventSummaryResponse de(Event evento) {
        return new EventSummaryResponse(
                evento.getId(),
                evento.getName(),
                evento.getVenue(),
                evento.getEventDate(),
                evento.getPrice(),
                evento.getTotalTickets(),
                evento.getImageUrl());
    }
}
