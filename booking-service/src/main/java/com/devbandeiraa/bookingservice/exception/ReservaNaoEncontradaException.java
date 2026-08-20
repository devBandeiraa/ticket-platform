package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * A reserva nao existe, ou existe e pertence a outra pessoa.
 *
 * <p>Traduzida em {@code 404 BOOKING_NOT_FOUND} nos dois casos. Responder {@code 403} quando a
 * reserva e de outro pareceria mais preciso, mas revelaria que aquele id existe — bastaria
 * varrer ids e observar a diferenca entre 403 e 404 para mapear o volume de reservas da
 * plataforma. Para quem nao e dono, a reserva simplesmente nao existe.
 */
public class ReservaNaoEncontradaException extends RuntimeException {

    public ReservaNaoEncontradaException(UUID id) {
        super("reserva %s nao encontrada".formatted(id));
    }
}
