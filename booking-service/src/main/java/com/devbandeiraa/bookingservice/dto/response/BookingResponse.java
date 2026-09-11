package com.devbandeiraa.bookingservice.dto.response;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingSeat;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reserva devolvida pela API.
 *
 * <p>{@code expiresAt} e o campo que o frontend usa para a contagem regressiva de pagamento.
 * Vem como instante absoluto, e nao como "faltam N segundos": um valor relativo calculado no
 * servidor comeca a envelhecer no caminho de volta, e o relogio do cliente pode estar adiantado.
 * Com o instante, o frontend calcula a diferenca no momento em que for exibir.
 *
 * <p>Nao ha mais {@code unitPrice}. Uma reserva de Plateia a 180 e Galeria a 70 nao tem preco
 * unitario — a media seria 125, valor que nenhum ingresso custou. No lugar dele vem a lista de
 * lugares, cada um com o que custou, que e mais informacao e nunca uma media inventada.
 *
 * <p>{@code seats} vem vazia nas reservas anteriores a Fase 17, feitas quando o sistema contava
 * ingressos sem saber quais eram. Inventar lugares para elas afirmaria algo que nunca se soube.
 */
public record BookingResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        int quantity,
        BigDecimal totalPrice,
        List<AssentoResponse> seats,
        BookingStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt) {

    /**
     * Um lugar da reserva.
     *
     * @param label como o lugar se chama para quem comprou: "Plateia A12". Montado no servidor
     *              para que a tela nao repita a regra de formatacao — e para que ela nao mude
     *              de um lugar do sistema para outro
     */
    public record AssentoResponse(
            UUID seatId,
            String sector,
            String row,
            int number,
            String label,
            BigDecimal price) {

        static AssentoResponse de(BookingSeat assento) {
            return new AssentoResponse(
                    assento.getSeatId(),
                    assento.getSectorName(),
                    assento.getRowLabel(),
                    assento.getSeatNumber(),
                    assento.getEtiqueta(),
                    assento.getPrice());
        }
    }

    public static BookingResponse de(Booking reserva, List<BookingSeat> assentos) {
        return new BookingResponse(
                reserva.getId(),
                reserva.getEventId(),
                reserva.getUserId(),
                reserva.getQuantity(),
                reserva.getTotalPrice(),
                assentos.stream().map(AssentoResponse::de).toList(),
                reserva.getStatus(),
                reserva.getExpiresAt(),
                reserva.getPaidAt(),
                reserva.getCreatedAt());
    }
}
