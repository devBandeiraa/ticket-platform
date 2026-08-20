package com.devbandeiraa.bookingservice.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da reserva.
 *
 * <p>O TTL define por quanto tempo uma reserva pendente segura o estoque. E um equilibrio entre
 * dois prejuizos: curto demais, o usuario perde a reserva enquanto digita os dados do cartao;
 * longo demais, ingressos ficam presos a carrinhos abandonados e indisponiveis para quem
 * compraria de fato.
 *
 * @param ttl                prazo para pagar antes de a reserva expirar
 * @param tamanhoMaximoDaChave limite da chave de idempotencia, igual ao da coluna no banco
 */
@ConfigurationProperties(prefix = "booking.reserva")
public record ReservaProperties(Duration ttl, int tamanhoMaximoDaChave) {

    public ReservaProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("booking.reserva.ttl precisa ser positivo");
        }
        if (tamanhoMaximoDaChave < 1) {
            throw new IllegalArgumentException(
                    "booking.reserva.tamanho-maximo-da-chave precisa ser positivo");
        }
    }
}
