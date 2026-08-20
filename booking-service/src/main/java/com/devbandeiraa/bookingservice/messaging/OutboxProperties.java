package com.devbandeiraa.bookingservice.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes do publicador da outbox.
 *
 * @param tamanhoDoLote quantas mensagens por varredura
 * @param maxTentativas depois de quantas falhas desistir de uma mensagem
 */
@ConfigurationProperties(prefix = "booking.outbox")
public record OutboxProperties(int tamanhoDoLote, int maxTentativas) {

    public OutboxProperties {
        if (tamanhoDoLote < 1) {
            throw new IllegalArgumentException("booking.outbox.tamanho-do-lote precisa ser positivo");
        }
        if (maxTentativas < 1) {
            throw new IllegalArgumentException("booking.outbox.max-tentativas precisa ser positivo");
        }
    }
}
