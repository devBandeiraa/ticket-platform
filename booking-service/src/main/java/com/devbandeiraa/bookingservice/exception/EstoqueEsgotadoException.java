package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * Nao ha ingressos suficientes para atender a reserva.
 *
 * <p>Traduzida em {@code 409 SOLD_OUT}. E o desfecho normal de quem perde a disputa pelos
 * ultimos ingressos — nao um erro do sistema, e sim a resposta correta.
 */
public class EstoqueEsgotadoException extends RuntimeException {

    public EstoqueEsgotadoException(UUID eventId, int solicitados) {
        super("nao ha %d ingresso(s) disponivel(is) para o evento %s".formatted(solicitados, eventId));
    }
}
