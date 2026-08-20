package com.devbandeiraa.notificationservice.notification;

import com.devbandeiraa.notificationservice.messaging.BookingConfirmedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Notificacao simulada por log estruturado.
 *
 * <p>Nao envia nada. O objetivo do projeto e o problema de concorrencia da reserva, e integrar um
 * provedor de e-mail acrescentaria credenciais, caixa de entrada e um servico externo no caminho
 * dos testes sem exercitar nada que ja nao esteja exercitado — a parte dificil e assincrona,
 * idempotente e com DLQ, e essa esta toda aqui.
 *
 * <p>O log sai com campos nomeados, e nao como frase montada. E o que permite filtrar por
 * {@code usuario=} ou {@code reserva=} numa ferramenta de log sem escrever expressao regular, e o
 * que um agregador consegue indexar.
 */
@Component
public class NotificacaoPorLog implements EnviadorDeNotificacao {

    private static final Logger log = LoggerFactory.getLogger(NotificacaoPorLog.class);

    private final Counter enviadas;

    public NotificacaoPorLog(MeterRegistry metricas) {
        this.enviadas = metricas.counter("notification.enviadas", "tipo", "booking.confirmed");
    }

    @Override
    public void confirmacaoDeReserva(BookingConfirmedEvent evento) {
        log.info("NOTIFICACAO tipo=booking.confirmed usuario={} reserva={} evento={} "
                        + "quantidade={} total={} confirmadaEm={}",
                evento.userId(),
                evento.bookingId(),
                evento.eventId(),
                evento.quantity(),
                evento.totalPrice(),
                evento.confirmedAt());

        enviadas.increment();
    }
}
