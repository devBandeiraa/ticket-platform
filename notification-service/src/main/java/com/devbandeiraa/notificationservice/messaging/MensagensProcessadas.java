package com.devbandeiraa.notificationservice.messaging;

import com.devbandeiraa.notificationservice.config.DeduplicacaoProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Lembra quais mensagens ja foram tratadas.
 *
 * <p>Existe por causa de uma escolha feita do outro lado. A outbox do booking-service garante
 * entrega <em>ao menos uma vez</em>, e nao exatamente uma: se a publicacao der certo e a marcacao
 * seguinte falhar, a mesma mensagem sai de novo. Fechar essa janela no produtor exigiria commit
 * distribuido entre banco e broker, que e justamente o que a outbox evita — de modo que a
 * responsabilidade cai aqui, e o consumidor precisa ser idempotente.
 *
 * <p>O registro fica no Redis, e nao em memoria, porque as duas situacoes em que duplicatas
 * aparecem sao exatamente aquelas em que um cache local nao serve: reinicio do processo e mais de
 * uma replica consumindo a mesma fila.
 */
@Component
public class MensagensProcessadas {

    private static final Logger log = LoggerFactory.getLogger(MensagensProcessadas.class);

    private static final String PREFIXO = "notification:processada:";

    private final StringRedisTemplate redis;
    private final DeduplicacaoProperties propriedades;
    private final Counter duplicatas;
    private final Counter degradacoes;

    public MensagensProcessadas(StringRedisTemplate redis, DeduplicacaoProperties propriedades,
                                MeterRegistry metricas) {
        this.redis = redis;
        this.propriedades = propriedades;
        this.duplicatas = metricas.counter("notification.duplicatas.descartadas");
        this.degradacoes = metricas.counter("notification.deduplicacao.degradacoes");
    }

    /**
     * Reserva o direito de tratar esta mensagem.
     *
     * <p>{@code SET NX PX} em uma unica instrucao: consultar e depois gravar deixaria entre as
     * duas uma janela na qual duas replicas se achariam a primeira, que e exatamente o caso que
     * este metodo existe para cobrir.
     *
     * <p>Com o Redis fora do ar, devolve verdadeiro e a mensagem e tratada. A escolha e a mesma
     * do lock distribuido do booking-service: entre arriscar uma notificacao repetida e nao
     * notificar, o dano da primeira e menor. Registrado em WARN e em metrica, para a degradacao
     * nao passar despercebida.
     *
     * @return {@code true} se esta e a primeira vez que a mensagem aparece
     */
    public boolean registrarSeNova(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            // Sem identificador nao ha como deduplicar. Tratar mesmo assim e melhor que descartar.
            log.warn("mensagem sem messageId: seguindo sem deduplicacao");
            return true;
        }

        try {
            Boolean primeiraVez = redis.opsForValue()
                    .setIfAbsent(PREFIXO + messageId, Instant.now().toString(), propriedades.ttl());

            if (!Boolean.TRUE.equals(primeiraVez)) {
                duplicatas.increment();
                return false;
            }

            return true;

        } catch (DataAccessException redisIndisponivel) {
            degradacoes.increment();
            log.warn("Redis indisponivel na deduplicacao da mensagem {}: tratando mesmo assim, "
                            + "com risco de notificacao repetida. Causa: {}",
                    messageId, redisIndisponivel.getMessage());
            return true;
        }
    }

    /**
     * Devolve o direito de tratar a mensagem, apos uma falha.
     *
     * <p>Sem isto, a deduplicacao trabalharia contra a entrega: a marca gravada antes do
     * processamento faria a retentativa ser reconhecida como duplicata e descartada, e a
     * notificacao se perderia justamente no caso em que o retry existe para salva-la.
     */
    public void liberar(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }

        try {
            redis.delete(PREFIXO + messageId);
        } catch (DataAccessException redisIndisponivel) {
            // A chave expira sozinha pelo TTL. Ate la, retentativas desta mensagem serao
            // descartadas como duplicatas — pior que o ideal, mas nao ha o que fazer daqui.
            log.warn("falha ao liberar a marca da mensagem {}; expirara em {}. Causa: {}",
                    messageId, propriedades.ttl(), redisIndisponivel.getMessage());
        }
    }
}
