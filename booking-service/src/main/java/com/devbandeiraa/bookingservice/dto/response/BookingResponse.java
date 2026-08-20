package com.devbandeiraa.bookingservice.dto.response;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva devolvida pela API.
 *
 * <p>{@code expiresAt} e o campo que o frontend usa para a contagem regressiva de pagamento.
 * Vem como instante absoluto, e nao como "faltam N segundos": um valor relativo calculado no
 * servidor comeca a envelhecer no caminho de volta, e o relogio do cliente pode estar adiantado.
 * Com o instante, o frontend calcula a diferenca no momento em que for exibir.
 */
public record BookingResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        BookingStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt) {

    public static BookingResponse de(Booking reserva) {
        return new BookingResponse(
                reserva.getId(),
                reserva.getEventId(),
                reserva.getUserId(),
                reserva.getQuantity(),
                reserva.getUnitPrice(),
                reserva.getTotalPrice(),
                reserva.getStatus(),
                reserva.getExpiresAt(),
                reserva.getPaidAt(),
                reserva.getCreatedAt());
    }
}
