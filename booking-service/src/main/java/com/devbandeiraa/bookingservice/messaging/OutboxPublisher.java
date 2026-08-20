package com.devbandeiraa.bookingservice.messaging;

import com.devbandeiraa.bookingservice.config.RabbitConfig;
import com.devbandeiraa.bookingservice.domain.OutboxMessage;
import com.devbandeiraa.bookingservice.repository.OutboxRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Entrega ao RabbitMQ o que foi gravado na outbox.
 *
 * <p>Segunda metade do padrao: a primeira gravou o evento junto com a transacao de negocio, esta
 * o publica. Separa-las e o que permite tentar de novo — uma falha aqui nao desfaz a reserva, e a
 * mensagem continua pendente para a proxima varredura.
 *
 * <p>A garantia e <strong>pelo menos uma vez</strong>. Se a publicacao der certo e a marcacao
 * seguinte falhar, a mensagem sai duplicada depois. Eliminar essa janela exigiria commit
 * distribuido entre banco e broker, que e exatamente o que a outbox existe para evitar; o preco e
 * o consumidor precisar ser idempotente.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties propriedades;
    private final Counter publicadas;
    private final Counter falhas;
    private final Counter descartadas;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           RabbitTemplate rabbitTemplate,
                           OutboxProperties propriedades,
                           MeterRegistry metricas) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.propriedades = propriedades;
        this.publicadas = metricas.counter("booking.outbox.publicadas");
        this.falhas = metricas.counter("booking.outbox.falhas");
        this.descartadas = metricas.counter("booking.outbox.descartadas");
    }

    /**
     * Publica o proximo lote de mensagens pendentes.
     *
     * <p>{@code fixedDelay} curto: a outbox troca a publicacao imediata por uma pequena latencia,
     * e esse intervalo e o tamanho dela. Dois segundos e imperceptivel para uma notificacao e
     * ainda deixa o servico com pouca varredura ociosa.
     */
    @Scheduled(
            fixedDelayString = "${booking.outbox.intervalo:2s}",
            initialDelayString = "${booking.outbox.atraso-inicial:10s}")
    public void publicarPendentes() {
        List<OutboxMessage> pendentes =
                outboxRepository.proximoLotePendente(propriedades.tamanhoDoLote());

        for (OutboxMessage mensagem : pendentes) {
            publicar(mensagem);
        }
    }

    /**
     * Publica uma mensagem.
     *
     * <p>Nao ha transacao envolvendo o lote, e nao deveria haver: uma mensagem problematica no
     * meio do caminho desfaria o registro de todas as publicadas antes dela — que ja sairam pelo
     * broker e nao voltam —, e a varredura seguinte republicaria tudo. Cada marcacao e
     * transacional por si, no proprio repositorio.
     *
     * <p>Este metodo tambem nao poderia ser {@code @Transactional} como esta: chamado de dentro
     * da mesma classe, nao passaria pelo proxy do Spring e a anotacao seria ignorada em silencio
     * — a mesma armadilha que levou {@code ReservaTransacional} a ser uma classe separada.
     */
    public void publicar(OutboxMessage mensagem) {
        try {
            enviar(mensagem);
        } catch (AmqpException falhaDoBroker) {
            tratarFalha(mensagem, falhaDoBroker);
            return;
        }

        // Se esta marcacao falhar, a mensagem continua pendente e sera publicada de novo. E a
        // janela de duplicidade inerente ao padrao, e a razao de o consumidor precisar ser
        // idempotente.
        if (outboxRepository.marcarComoPublicada(mensagem.getId(), Instant.now()) == 0) {
            // Outra replica marcou primeiro. Ambas publicaram, e o consumidor recebera duas
            // copias — de novo, resolvido do lado de la, por idempotencia.
            log.debug("mensagem {} ja havia sido marcada como publicada por outra instancia",
                    mensagem.getId());
            return;
        }

        publicadas.increment();
        log.info("evento publicado: tipo={} agregado={} mensagem={}",
                mensagem.getType(), mensagem.getAggregateId(), mensagem.getId());
    }

    private void enviar(OutboxMessage mensagem) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                mensagem.getType(),
                mensagem.getPayload(),
                propriedadesDaMensagem -> {
                    // Persistente: sem isso a mensagem vive so na memoria do broker, e um
                    // reinicio do RabbitMQ apagaria justamente o que a outbox se esforcou para
                    // nao perder.
                    propriedadesDaMensagem.getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // Deixa o consumidor desserializar como JSON sem depender do nome da classe
                    // do produtor, que e detalhe interno deste servico.
                    propriedadesDaMensagem.getMessageProperties().setContentType("application/json");
                    propriedadesDaMensagem.getMessageProperties()
                            .setMessageId(mensagem.getId().toString());
                    return propriedadesDaMensagem;
                });
    }

    private void tratarFalha(OutboxMessage mensagem, AmqpException causa) {
        falhas.increment();
        String erro = causa.getMessage();

        if (mensagem.getAttempts() + 1 >= propriedades.maxTentativas()) {
            descartadas.increment();
            outboxRepository.desistir(mensagem.getId(), erro);

            // ERROR, e nao WARN: aqui uma notificacao foi definitivamente perdida. A reserva
            // segue paga e correta — o dinheiro nao depende disto —, mas alguem precisa saber.
            log.error("desistindo da mensagem {} apos {} tentativas: {}",
                    mensagem.getId(), propriedades.maxTentativas(), erro);
            return;
        }

        outboxRepository.registrarFalha(mensagem.getId(), erro);
        log.warn("falha ao publicar a mensagem {} (tentativa {}): {}. Sera tentada de novo.",
                mensagem.getId(), mensagem.getAttempts() + 1, erro);
    }
}
