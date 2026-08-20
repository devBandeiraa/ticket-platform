package com.devbandeiraa.bookingservice.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da varredura de reservas vencidas.
 *
 * @param tamanhoDoLote quantas reservas por execucao
 */
@ConfigurationProperties(prefix = "booking.expiracao")
public record ExpiracaoProperties(int tamanhoDoLote) {

    public ExpiracaoProperties {
        if (tamanhoDoLote < 1) {
            throw new IllegalArgumentException("booking.expiracao.tamanho-do-lote precisa ser positivo");
        }
    }
}
