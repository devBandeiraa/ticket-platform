package com.devbandeiraa.bookingservice.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Endereco e limites de tempo da chamada ao event-service.
 *
 * <p>Os timeouts sao curtos de proposito. Esta chamada acontece uma unica vez por evento, na
 * hidratacao do estoque, mas acontece dentro do pedido de reserva de alguem. Sem limite de
 * tempo, um event-service lento nao devolveria erro: prenderia threads do booking-service
 * esperando, ate que o proprio booking-service parasse de responder por esgotamento do pool —
 * uma falha se espalhando para um servico que estava saudavel.
 *
 * @param url            base do event-service
 * @param connectTimeout limite para estabelecer a conexao
 * @param readTimeout    limite para a resposta chegar
 */
@ConfigurationProperties(prefix = "booking.event-service")
public record EventServiceProperties(String url, Duration connectTimeout, Duration readTimeout) {

    public EventServiceProperties {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("booking.event-service.url e obrigatoria");
        }
    }
}
