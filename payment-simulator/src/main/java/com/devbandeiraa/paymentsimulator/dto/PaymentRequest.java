package com.devbandeiraa.paymentsimulator.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cobranca pedida pelo booking-service.
 *
 * @param bookingId reserva sendo paga, so para o log do simulador fazer sentido
 * @param amount    valor total
 */
public record PaymentRequest(
        @NotNull UUID bookingId,
        @NotNull @DecimalMin(value = "0.01", message = "o valor deve ser positivo") BigDecimal amount) {
}
