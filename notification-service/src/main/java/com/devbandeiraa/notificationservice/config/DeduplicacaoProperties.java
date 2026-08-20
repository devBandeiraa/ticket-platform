package com.devbandeiraa.notificationservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da deduplicacao de mensagens.
 *
 * @param ttl por quanto tempo lembrar de uma mensagem ja tratada
 */
@ConfigurationProperties(prefix = "notification.deduplicacao")
public record DeduplicacaoProperties(Duration ttl) {

    public DeduplicacaoProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("notification.deduplicacao.ttl precisa ser positivo");
        }
    }
}
