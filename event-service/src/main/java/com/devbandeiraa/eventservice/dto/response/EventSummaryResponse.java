package com.devbandeiraa.eventservice.dto.response;

import com.devbandeiraa.eventservice.domain.Event;
import com.devbandeiraa.eventservice.domain.EventCategory;
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
 * o que a listagem desenha, e nao pelo tamanho do campo. A categoria entra pela mesma razao — o
 * cartao a mostra como etiqueta, e sem ela a listagem teria de buscar o detalhe de cada evento
 * so para saber o que escrever.
 */
public record EventSummaryResponse(
        UUID id,
        String name,
        String venue,
        Instant eventDate,
        BigDecimal price,
        int totalTickets,
        String imageUrl,
        EventCategory category) {

    public static EventSummaryResponse de(Event evento) {
        return new EventSummaryResponse(
                evento.getId(),
                evento.getName(),
                evento.getVenue(),
                evento.getEventDate(),
                evento.getPrice(),
                evento.getTotalTickets(),
                evento.getImageUrl(),
                evento.getCategory());
    }
}
