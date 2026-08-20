package com.devbandeiraa.notificationservice.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma reserva foi paga.
 *
 * <p>Copia deliberada do record de mesmo nome no booking-service, e nao um tipo compartilhado. A
 * tentacao de extrair para um modulo comum e forte e seria um erro: o contrato entre servicos e a
 * mensagem no broker, nao uma classe Java. Com um tipo compartilhado, mudar o evento passaria a
 * exigir recompilar e reimplantar os dois servicos ao mesmo tempo — que e precisamente o
 * acoplamento que a mensageria existe para desfazer.
 *
 * <p>{@code ignoreUnknown} pelo mesmo motivo: o produtor pode acrescentar campos, e este
 * consumidor deve continuar funcionando sem saber deles.
 *
 * <p>Note que nao ha e-mail nem nome do usuario. Esses dados pertencem ao auth-service, e o
 * evento nao os carrega de proposito. Ver {@code EnviadorDeNotificacao}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingConfirmedEvent(
        UUID bookingId,
        UUID eventId,
        UUID userId,
        int quantity,
        BigDecimal totalPrice,
        Instant confirmedAt) {
}
