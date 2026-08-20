package com.devbandeiraa.bookingservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Pedido de reserva.
 *
 * <p>Nao ha campo de preco nem de usuario. O preco vem do estoque local, e o usuario vem do
 * token: aceitar qualquer um dos dois no corpo permitiria reservar em nome de outra pessoa ou
 * definir o proprio preco.
 *
 * @param eventId  evento a reservar
 * @param quantity quantos ingressos
 */
public record CreateBookingRequest(

        @NotNull(message = "eventId e obrigatorio")
        UUID eventId,

        // Sem limite superior por enquanto: um pedido absurdo simplesmente nao cabe no estoque e
        // recebe 409 SOLD_OUT. Um teto por reserva e regra de negocio, e ainda nao foi definida.
        @Min(value = 1, message = "quantity precisa ser no minimo 1")
        int quantity) {
}
