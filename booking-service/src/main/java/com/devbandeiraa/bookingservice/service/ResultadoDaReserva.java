package com.devbandeiraa.bookingservice.service;

import com.devbandeiraa.bookingservice.dto.response.BookingResponse;

/**
 * Reserva devolvida, com a informacao de ter sido criada agora ou apenas reapresentada.
 *
 * <p>O controller precisa dessa distincao para escolher entre {@code 201 Created} e {@code 200
 * OK}. Devolver 201 na repeticao de um pedido idempotente diria ao cliente que algo novo foi
 * criado, quando nada foi — e um cliente que conte reservas pela contagem de 201 passaria a
 * contar errado.
 *
 * @param reserva a reserva
 * @param nova    {@code true} se foi criada nesta requisicao
 */
public record ResultadoDaReserva(BookingResponse reserva, boolean nova) {

    public static ResultadoDaReserva criada(BookingResponse reserva) {
        return new ResultadoDaReserva(reserva, true);
    }

    public static ResultadoDaReserva repetida(BookingResponse reserva) {
        return new ResultadoDaReserva(reserva, false);
    }
}
