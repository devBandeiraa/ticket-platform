package com.devbandeiraa.bookingservice.messaging;

import com.devbandeiraa.bookingservice.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma reserva foi paga.
 *
 * <p>Carrega fatos da reserva, e nada alem disso. Em particular, nao carrega o e-mail do usuario:
 * esse dado pertence ao auth-service, e copia-lo para dentro de um evento do booking-service o
 * transformaria em fonte de uma informacao que ele nao possui — quando o usuario trocasse de
 * e-mail, o evento seguiria carregando o antigo. Cabe ao notification-service, na Fase 5,
 * resolver como alcancar o usuario a partir do {@code userId}.
 *
 * <p>O nome no passado nao e detalhe de estilo: evento descreve o que ja aconteceu e nao pode ser
 * recusado por quem o recebe. Um "confirmarReserva" seria um comando, e comando pressupoe que o
 * destinatario possa dizer nao.
 */
public record BookingConfirmedEvent(
        UUID bookingId,
        UUID eventId,
        UUID userId,
        int quantity,
        BigDecimal totalPrice,
        Instant confirmedAt) {

    /** Nome usado como routing key na publicacao. */
    public static final String TIPO = "booking.confirmed";

    public static BookingConfirmedEvent de(Booking reserva) {
        return new BookingConfirmedEvent(
                reserva.getId(),
                reserva.getEventId(),
                reserva.getUserId(),
                reserva.getQuantity(),
                reserva.getTotalPrice(),
                reserva.getPaidAt());
    }
}
