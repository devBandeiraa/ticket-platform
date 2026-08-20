package com.devbandeiraa.notificationservice.messaging;

import com.devbandeiraa.notificationservice.config.RabbitConfig;
import com.devbandeiraa.notificationservice.notification.EnviadorDeNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consome as confirmacoes de reserva publicadas pelo booking-service.
 *
 * <p>O metodo faz tres coisas, nesta ordem, e a ordem e o que sustenta as garantias:
 *
 * <ol>
 *   <li><strong>Reserva o direito de tratar.</strong> Se a mensagem ja apareceu, descarta com ack
 *       — ela nao volta para a fila nem vai para a DLQ, porque nao houve erro: a entrega
 *       duplicada e o comportamento esperado da outbox do produtor.
 *   <li><strong>Notifica.</strong>
 *   <li><strong>Devolve o direito, se falhar.</strong> Sem isso a marca gravada no passo 1 faria
 *       a retentativa ser vista como duplicata e descartada, e a notificacao se perderia
 *       exatamente no caso em que o retry existe para salva-la.
 * </ol>
 */
@Component
public class BookingConfirmedListener {

    private static final Logger log = LoggerFactory.getLogger(BookingConfirmedListener.class);

    private final MensagensProcessadas mensagensProcessadas;
    private final EnviadorDeNotificacao enviador;

    public BookingConfirmedListener(MensagensProcessadas mensagensProcessadas,
                                    EnviadorDeNotificacao enviador) {
        this.mensagensProcessadas = mensagensProcessadas;
        this.enviador = enviador;
    }

    /**
     * Trata uma confirmacao.
     *
     * <p>Uma excecao lancada daqui e o que aciona a retentativa e, esgotadas as tentativas, o
     * envio para a DLQ. Engolir a falha com um {@code try/catch} silencioso quebraria as duas
     * coisas de uma vez: a mensagem receberia ack e sumiria, e ninguem saberia que a notificacao
     * nao saiu.
     */
    @RabbitListener(queues = RabbitConfig.FILA_CONFIRMADA)
    public void receber(@Payload BookingConfirmedEvent evento,
                        @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {

        if (!mensagensProcessadas.registrarSeNova(messageId)) {
            log.info("mensagem {} ja tratada: descartando duplicata da reserva {}",
                    messageId, evento.bookingId());
            return;
        }

        try {
            enviador.confirmacaoDeReserva(evento);
        } catch (RuntimeException falha) {
            mensagensProcessadas.liberar(messageId);
            throw falha;
        }
    }
}
