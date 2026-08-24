package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * O provedor avaliou a cobranca e a negou.
 *
 * <p>Desfecho definitivo: repetir devolve exatamente isto de novo. Fica na lista de
 * {@code ignore-exceptions} do retry justamente por isso.
 */
public class PagamentoRecusadoException extends RuntimeException {

    public PagamentoRecusadoException(UUID reservaId, String motivo) {
        super("cobranca da reserva %s recusada: %s".formatted(reservaId, motivo));
    }
}
