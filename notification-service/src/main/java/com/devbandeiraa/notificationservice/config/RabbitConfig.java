package com.devbandeiraa.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Filas do notification-service.
 *
 * <p>Quem consome declara a propria fila e o proprio binding; o booking-service declara apenas o
 * exchange. E o que mantem os dois desacoplados — este servico escolhe o que quer ouvir sem
 * exigir mudanca do outro lado, e um consumidor novo entraria do mesmo jeito.
 *
 * <p>O exchange e redeclarado aqui, e nao importado do booking-service. Declarar um exchange e
 * idempotente no RabbitMQ: se ja existe com os mesmos atributos, nada acontece. A alternativa
 * seria depender da ordem de subida dos servicos, e um consumidor que so funciona se o produtor
 * subiu primeiro nao e desacoplado coisa nenhuma.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "booking.exchange";
    public static final String ROUTING_KEY_CONFIRMADA = "booking.confirmed";
    public static final String FILA_CONFIRMADA = "notification.booking.confirmed";

    /** Para onde vao as mensagens que esgotaram as tentativas. */
    public static final String DLX = "notification.dlx";
    public static final String FILA_MORTA = "notification.booking.confirmed.dlq";

    @Bean
    TopicExchange bookingExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    /**
     * Fila principal.
     *
     * <p>Durable e com dead-letter configurado. Sem {@code durable}, um reinicio do broker
     * apagaria a fila e as mensagens publicadas enquanto este servico estivesse fora do ar se
     * perderiam — anulando boa parte do que a outbox do booking-service se esforcou para
     * garantir.
     */
    @Bean
    Queue filaDeConfirmacao() {
        return QueueBuilder.durable(FILA_CONFIRMADA)
                .deadLetterExchange(DLX)
                .deadLetterRoutingKey(FILA_MORTA)
                .build();
    }

    @Bean
    Binding bindingDeConfirmacao(Queue filaDeConfirmacao, TopicExchange bookingExchange) {
        return BindingBuilder.bind(filaDeConfirmacao).to(bookingExchange).with(ROUTING_KEY_CONFIRMADA);
    }

    /**
     * Exchange de mensagens mortas.
     *
     * <p>{@code direct}, e nao {@code topic}: aqui nao ha roteamento a decidir. Uma mensagem que
     * falhou tem um unico destino, e o padrao de nomes que faria sentido num topic seria
     * ornamento.
     */
    @Bean
    DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    /**
     * Fila morta.
     *
     * <p>Ela existe para ser olhada por uma pessoa. Uma mensagem que chega aqui falhou o numero
     * configurado de vezes, o que quase sempre significa defeito no consumidor ou payload
     * inesperado — nenhum dos dois se resolve tentando de novo. Descartar em silencio esconderia
     * o problema; reprocessar para sempre travaria a fila principal.
     */
    @Bean
    Queue filaMorta() {
        return QueueBuilder.durable(FILA_MORTA).build();
    }

    @Bean
    Binding bindingDaFilaMorta(Queue filaMorta, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(filaMorta).to(deadLetterExchange).with(FILA_MORTA);
    }

    /**
     * Desserializa o corpo JSON das mensagens para o record do evento.
     *
     * <p>Recebe o {@code ObjectMapper} da aplicacao em vez de criar um proprio: o evento tem um
     * campo {@code Instant}, e so o mapper configurado pelo Spring Boot traz o modulo de datas
     * do Java 8 registrado. Um mapper novo falharia em ler {@code confirmedAt}.
     *
     * <p>O produtor nao envia cabecalho de tipo, o que e intencional do lado de la — o nome da
     * classe Java dele e detalhe interno e nao deve virar parte do contrato. O tipo alvo vem da
     * assinatura do metodo anotado com {@code @RabbitListener}.
     */
    @Bean
    MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
