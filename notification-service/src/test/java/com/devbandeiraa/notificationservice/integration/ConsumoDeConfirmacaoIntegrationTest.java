package com.devbandeiraa.notificationservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.devbandeiraa.notificationservice.config.RabbitConfig;
import com.devbandeiraa.notificationservice.messaging.BookingConfirmedEvent;
import com.devbandeiraa.notificationservice.notification.EnviadorDeNotificacao;
import com.devbandeiraa.notificationservice.support.TestcontainersConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Testes do consumo de {@code booking.confirmed}.
 *
 * <p>As mensagens sao publicadas exatamente como o booking-service publica — corpo JSON,
 * {@code content-type} de JSON e {@code message-id} preenchido — para que o que se verifica seja
 * o contrato entre os dois servicos, e nao um formato inventado para o teste passar.
 *
 * <p>O {@code EnviadorDeNotificacao} e simulado: o interesse aqui e roteamento, deduplicacao e
 * dead-lettering, e um mock permite tanto contar as entregas quanto provocar falhas sob demanda.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class ConsumoDeConfirmacaoIntegrationTest {

    private static final Duration ATE = Duration.ofSeconds(10);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redis;

    @MockitoBean
    private EnviadorDeNotificacao enviador;

    @BeforeEach
    void limparEstado() {
        // Sem isto, a marca de deduplicacao de um teste faria o seguinte descartar a mensagem.
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        esvaziar(RabbitConfig.FILA_CONFIRMADA);
        esvaziar(RabbitConfig.FILA_MORTA);
    }

    @Test
    @DisplayName("uma confirmacao publicada vira uma notificacao")
    void deveNotificarConfirmacao() {
        UUID reservaId = UUID.randomUUID();

        publicar(reservaId, UUID.randomUUID().toString());

        await().atMost(ATE).untilAsserted(() -> {
            verify(enviador).confirmacaoDeReserva(any(BookingConfirmedEvent.class));
        });
    }

    @Test
    @DisplayName("os campos do evento chegam intactos, inclusive a data")
    void deveDesserializarOEventoPorInteiro() {
        UUID reservaId = UUID.randomUUID();
        UUID eventoId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();

        publicar(reservaId, eventoId, usuarioId, UUID.randomUUID().toString());

        await().atMost(ATE).untilAsserted(() -> {
            org.mockito.ArgumentCaptor<BookingConfirmedEvent> capturador =
                    org.mockito.ArgumentCaptor.forClass(BookingConfirmedEvent.class);
            verify(enviador).confirmacaoDeReserva(capturador.capture());

            BookingConfirmedEvent recebido = capturador.getValue();
            assertThat(recebido.bookingId()).isEqualTo(reservaId);
            assertThat(recebido.eventId()).isEqualTo(eventoId);
            assertThat(recebido.userId()).isEqualTo(usuarioId);
            assertThat(recebido.quantity()).isEqualTo(2);
            // Instant so desserializa porque o conversor usa o ObjectMapper da aplicacao, com o
            // modulo de datas do Java 8 registrado.
            assertThat(recebido.confirmedAt()).isNotNull();
        });
    }

    // ---------- deduplicacao ----------

    @Test
    @DisplayName("a mesma mensagem entregue duas vezes notifica uma vez so")
    void deveDescartarDuplicata() {
        UUID reservaId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();

        // E o que a outbox do booking-service faz quando publica com sucesso mas falha ao
        // marcar a mensagem como publicada: a mesma mensagem sai de novo no ciclo seguinte.
        publicar(reservaId, messageId);
        publicar(reservaId, messageId);

        await().atMost(ATE).untilAsserted(() ->
                verify(enviador).confirmacaoDeReserva(any(BookingConfirmedEvent.class)));

        // Espera um pouco alem para garantir que a segunda foi de fato descartada, e nao apenas
        // ainda nao processada.
        await().pollDelay(Duration.ofSeconds(1)).atMost(ATE).untilAsserted(() ->
                verify(enviador, times(1)).confirmacaoDeReserva(any(BookingConfirmedEvent.class)));
    }

    @Test
    @DisplayName("mensagens distintas da mesma reserva sao ambas tratadas")
    void naoDeveConfundirDeduplicacaoComReserva() {
        UUID reservaId = UUID.randomUUID();

        // A deduplicacao e por mensagem, e nao por reserva: eventos diferentes sobre a mesma
        // reserva sao fatos diferentes e nao podem ser engolidos um pelo outro.
        publicar(reservaId, UUID.randomUUID().toString());
        publicar(reservaId, UUID.randomUUID().toString());

        await().atMost(ATE).untilAsserted(() ->
                verify(enviador, times(2)).confirmacaoDeReserva(any(BookingConfirmedEvent.class)));
    }

    // ---------- retentativa e DLQ ----------

    @Test
    @DisplayName("falha transitoria e superada pela retentativa")
    void deveRetentarEEntregar() {
        doThrow(new IllegalStateException("provedor instavel"))
                .doNothing()
                .when(enviador).confirmacaoDeReserva(any(BookingConfirmedEvent.class));

        publicar(UUID.randomUUID(), UUID.randomUUID().toString());

        await().atMost(ATE).untilAsserted(() ->
                verify(enviador, times(2)).confirmacaoDeReserva(any(BookingConfirmedEvent.class)));

        // A retentativa so funciona porque o listener devolve a marca de deduplicacao ao falhar.
        // Sem isso, a segunda tentativa seria descartada como duplicata e a notificacao se
        // perderia justamente quando o retry existe para salva-la.
        assertThat(rabbitTemplate.receive(RabbitConfig.FILA_MORTA, 500)).isNull();
    }

    @Test
    @DisplayName("falha permanente esgota as tentativas e a mensagem vai para a DLQ")
    void deveMandarParaDlqAposOLimite() {
        doThrow(new IllegalStateException("defeito permanente"))
                .when(enviador).confirmacaoDeReserva(any(BookingConfirmedEvent.class));

        publicar(UUID.randomUUID(), UUID.randomUUID().toString());

        // Tres tentativas, e entao a mensagem sai da fila principal para a DLQ, onde fica
        // disponivel para alguem olhar em vez de ser descartada em silencio.
        await().atMost(ATE).untilAsserted(() ->
                verify(enviador, times(3)).confirmacaoDeReserva(any(BookingConfirmedEvent.class)));

        Message morta = rabbitTemplate.receive(RabbitConfig.FILA_MORTA, 5000);
        assertThat(morta).isNotNull();
    }

    @Test
    @DisplayName("payload malformado vai direto para a DLQ, sem retentativa")
    void deveMandarPayloadInvalidoParaDlq() {
        publicarCorpoCru("{ isto nao e json valido", UUID.randomUUID().toString());

        // Retentar nao consertaria um JSON quebrado. O Spring AMQP trata falha de conversao como
        // erro fatal e recusa a mensagem de imediato.
        Message morta = rabbitTemplate.receive(RabbitConfig.FILA_MORTA, 5000);
        assertThat(morta).isNotNull();
        verify(enviador, never()).confirmacaoDeReserva(any(BookingConfirmedEvent.class));
    }

    // ---------- apoio ----------

    private void publicar(UUID reservaId, String messageId) {
        publicar(reservaId, UUID.randomUUID(), UUID.randomUUID(), messageId);
    }

    private void publicar(UUID reservaId, UUID eventoId, UUID usuarioId, String messageId) {
        publicarCorpoCru("""
                {
                  "bookingId": "%s",
                  "eventId": "%s",
                  "userId": "%s",
                  "quantity": 2,
                  "totalPrice": 300.00,
                  "confirmedAt": "2026-08-20T12:00:00Z"
                }
                """.formatted(reservaId, eventoId, usuarioId), messageId);
    }

    /** Publica exatamente como o booking-service: corpo JSON cru, com content-type e message-id. */
    private void publicarCorpoCru(String json, String messageId) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setMessageId(messageId);

        rabbitTemplate.send(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_KEY_CONFIRMADA,
                new Message(json.getBytes(java.nio.charset.StandardCharsets.UTF_8), propriedades));
    }

    private void esvaziar(String fila) {
        while (rabbitTemplate.receive(fila, 100) != null) {
            // Descarta o que sobrou de um teste anterior.
        }
    }
}
