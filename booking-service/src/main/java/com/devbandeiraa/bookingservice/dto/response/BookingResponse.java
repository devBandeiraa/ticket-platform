package com.devbandeiraa.bookingservice.dto.response;

import com.devbandeiraa.bookingservice.domain.Booking;
import com.devbandeiraa.bookingservice.domain.BookingSeat;
import com.devbandeiraa.bookingservice.domain.BookingStatus;
import com.devbandeiraa.bookingservice.domain.PaymentMethod;
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
 *
 * <p>{@code subtotal}, {@code fee} e {@code totalPrice} vem os tres, em vez de so o total com a
 * taxa embutida: a tela de checkout precisa mostrar a decomposicao, e recalcula-la no cliente
 * duplicaria a regra de arredondamento em outra linguagem. Somam sempre — e uma invariante que o
 * banco tambem guarda, em {@code ck_bookings_total_fecha}.
 *
 * <p>{@code paymentMethod} e {@code ticketCode} sao nulos enquanto a reserva nao foi paga, e
 * tambem nas reservas confirmadas antes da Fase 22, que foram pagas quando os campos nao
 * existiam. A tela do ingresso trata os dois casos igual: sem codigo, nao ha ingresso a desenhar.
 */
public record BookingResponse(
        UUID id,
        UUID eventId,
        UUID userId,
        int quantity,
        BigDecimal subtotal,
        BigDecimal fee,
        BigDecimal totalPrice,
        List<AssentoResponse> seats,
        BookingStatus status,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt,
        PaymentMethod paymentMethod,
        String ticketCode) {

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
                reserva.getSubtotal(),
                reserva.getFee(),
                reserva.getTotalPrice(),
                assentos.stream().map(AssentoResponse::de).toList(),
                reserva.getStatus(),
                reserva.getExpiresAt(),
                reserva.getPaidAt(),
                reserva.getCreatedAt(),
                reserva.getPaymentMethod(),
                reserva.getTicketCode());
    }
}
