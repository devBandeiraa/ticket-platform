package com.devbandeiraa.bookingservice.messaging;

import com.devbandeiraa.bookingservice.domain.OutboxMessage;
import com.devbandeiraa.bookingservice.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Grava eventos na outbox.
 *
 * <p>Nao abre transacao propria, e essa ausencia e o ponto: precisa correr dentro da transacao de
 * quem chama, para que o evento e o fato que o originou sejam gravados juntos ou nao sejam
 * gravados. Uma transacao propria aqui desfaria justamente a garantia que a outbox existe para
 * dar.
 */
@Component
public class OutboxRegistrar {

    private static final String AGREGADO_RESERVA = "booking";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ContextoDeTrace contextoDeTrace;

    public OutboxRegistrar(OutboxRepository outboxRepository, ObjectMapper objectMapper,
                           ContextoDeTrace contextoDeTrace) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.contextoDeTrace = contextoDeTrace;
    }

    /**
     * Registra que uma reserva foi paga.
     *
     * <p>O contexto de trace e capturado <em>aqui</em>, e nao na publicacao, porque aqui ainda se
     * esta dentro da requisicao que pagou a reserva. Na publicacao, segundos depois, esse contexto
     * ja nao existe — e capturado la produziria a arvore do job, nao a da compra.
     */
    public void registrarConfirmacao(BookingConfirmedEvent evento) {
        outboxRepository.save(OutboxMessage.pendente(
                AGREGADO_RESERVA,
                evento.bookingId(),
                BookingConfirmedEvent.TIPO,
                serializar(evento),
                contextoDeTrace.capturar()));
    }

    /**
     * A serializacao acontece aqui, na gravacao, e nao na hora de publicar.
     *
     * <p>Falhar em serializar significa que o evento nao pode ser representado — e um defeito de
     * programacao, nao uma condicao de execucao. Descobrir isso agora derruba a transacao inteira
     * e o problema aparece no primeiro teste. Descobri-lo na publicacao, horas depois, deixaria
     * uma reserva confirmada com um evento impossivel de enviar.
     */
    private String serializar(Object evento) {
        try {
            return objectMapper.writeValueAsString(evento);
        } catch (JsonProcessingException falha) {
            throw new IllegalStateException(
                    "nao foi possivel serializar o evento " + evento.getClass().getSimpleName(),
                    falha);
        }
    }
}
