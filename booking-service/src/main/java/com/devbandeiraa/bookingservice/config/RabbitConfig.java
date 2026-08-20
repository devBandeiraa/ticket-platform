package com.devbandeiraa.bookingservice.config;

import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publicacao de eventos de reserva.
 *
 * <p>Declara o exchange e mais nada. Fila e binding sao declarados por quem consome — o
 * notification-service, na Fase 5 — e essa divisao e o que mantem os servicos desacoplados: o
 * booking-service anuncia que uma reserva foi paga sem saber quem se interessa, e um consumidor
 * novo entra criando a propria fila, sem exigir mudanca aqui.
 *
 * <p>Exchange do tipo topic, e nao direct: a routing key {@code booking.confirmed} permite que um
 * consumidor futuro assine {@code booking.*} e receba tambem cancelamentos e expiracoes, se
 * vierem a ser publicados.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "booking.exchange";

    /**
     * Durable: o exchange sobrevive a um reinicio do broker.
     *
     * <p>Sem isso, reiniciar o RabbitMQ apagaria a declaracao e as publicacoes seguintes cairiam
     * no vazio — o RabbitMQ aceita publicar em exchange inexistente sem reclamar, o que
     * transformaria a perda de mensagens em algo silencioso.
     */
    @Bean
    TopicExchange bookingExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }
}
