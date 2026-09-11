package com.devbandeiraa.bookingservice.dto.response;

import com.devbandeiraa.bookingservice.service.EstoqueService;
import java.util.UUID;

/**
 * Quantos lugares o evento tem, e quantos restam.
 *
 * <p>Os numeros sao <strong>contados</strong> sobre os proprios assentos, e nao lidos de um
 * contador. Um contador de reservados ao lado de uma linha por lugar seriam duas fontes de
 * verdade para a mesma pergunta, e duas fontes divergem — foi por isso que a tabela de estoque
 * deixou de existir na Fase 17.
 *
 * <p>O numero e um retrato do instante da consulta: sob concorrencia ele muda entre a leitura e
 * a reserva, e e por isso que a decisao de vender nao se apoia nele. Quem decide e o
 * {@code UPDATE} condicional, na hora de tomar o lugar.
 */
public record AvailabilityResponse(UUID eventId, long total, long reserved, long available) {

    public static AvailabilityResponse de(EstoqueService.Disponibilidade disponibilidade) {
        return new AvailabilityResponse(
                disponibilidade.eventId(),
                disponibilidade.total(),
                disponibilidade.reservados(),
                disponibilidade.disponiveis());
    }
}
