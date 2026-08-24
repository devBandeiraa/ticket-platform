package com.devbandeiraa.paymentsimulator.dto;

import java.util.UUID;

/**
 * Autorizacao concedida.
 *
 * @param bookingId         reserva paga
 * @param authorizationCode comprovante, estavel para a mesma chave de idempotencia
 * @param repetida          se esta resposta veio do registro anterior em vez de uma cobranca nova
 */
public record PaymentResponse(UUID bookingId, String authorizationCode, boolean repetida) {
}
