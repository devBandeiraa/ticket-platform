package com.devbandeiraa.bookingservice.dto.response;

import com.devbandeiraa.bookingservice.domain.EventSeat;
import com.devbandeiraa.bookingservice.domain.SeatStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * O mapa da casa, com o estado de cada lugar.
 *
 * <p>Vem deste servico, e nao do event-service, porque a pergunta que o mapa responde e "quais
 * lugares posso escolher?" — e quem sabe o que ja foi vendido e quem guarda o estado. O
 * event-service responde pela planta; este, por quem esta sentado onde.
 *
 * <p>{@code RESERVED} e {@code SOLD} chegam distintos ao cliente de proposito, embora ambos
 * signifiquem "nao da para escolher". Um lugar reservado pode voltar a ficar livre quando o
 * prazo de pagamento vencer, e a tela pode dizer isso a quem esta esperando uma desistencia.
 */
public record SeatMapResponse(UUID eventId, List<AssentoDoMapa> seats) {

    /**
     * Um lugar no mapa.
     *
     * @param label como o lugar se chama: "Plateia A12". Montado no servidor para que a tela
     *              nao repita a regra de formatacao
     */
    public record AssentoDoMapa(
            UUID seatId,
            String sector,
            String row,
            int number,
            String label,
            BigDecimal price,
            SeatStatus status) {

        static AssentoDoMapa de(EventSeat assento) {
            return new AssentoDoMapa(
                    assento.getId(),
                    assento.getSectorName(),
                    assento.getRowLabel(),
                    assento.getSeatNumber(),
                    assento.getEtiqueta(),
                    assento.getPrice(),
                    assento.getStatus());
        }
    }

    public static SeatMapResponse de(UUID eventId, List<EventSeat> assentos) {
        return new SeatMapResponse(eventId, assentos.stream().map(AssentoDoMapa::de).toList());
    }
}
