package com.devbandeiraa.bookingservice.lock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Lock distribuido sobre Redis.
 *
 * <p>Aquisicao por {@code SET chave token NX PX ttl}: uma unica instrucao que so grava se a
 * chave nao existir e ja define a validade. Fazer em dois passos — {@code SETNX} e depois
 * {@code EXPIRE} — pareceria equivalente, mas um processo que morresse entre os dois deixaria
 * uma chave sem validade nenhuma, travando as reservas daquele evento para sempre.
 */
@Component
public class RedisDistributedLock implements DistributedLock {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLock.class);

    /**
     * Liberacao por comparacao de token, executada como script Lua para ser atomica.
     *
     * <p>Um {@code DEL} direto seria um bug sutil e serio. Imagine: o processo A adquire o lock,
     * a secao critica demora mais que o TTL, o lock expira, o processo B o adquire — e entao A
     * termina e apaga a chave. A acabou de matar o lock de B, que segue se achando dono. Comparar
     * o token antes de apagar impede isso, e a comparacao precisa ser atomica com o apagar: entre
     * um {@code GET} e um {@code DEL} feitos em chamadas separadas cabe exatamente a mesma
     * corrida.
     */
    private static final RedisScript<Long> LIBERACAO_SEGURA = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redis;
    private final LockProperties propriedades;
    private final Counter adquiridos;
    private final Counter naoAdquiridos;
    private final Counter degradacoes;
    private final Counter expiradosNaSecaoCritica;

    public RedisDistributedLock(StringRedisTemplate redis, LockProperties propriedades,
                                MeterRegistry metricas) {
        this.redis = redis;
        this.propriedades = propriedades;
        this.adquiridos = metricas.counter("booking.lock.adquiridos");
        this.naoAdquiridos = metricas.counter("booking.lock.nao.adquiridos");
        this.degradacoes = metricas.counter("booking.lock.degradacoes");
        this.expiradosNaSecaoCritica = metricas.counter("booking.lock.expirados");
    }

    @Override
    public <T> T executarComLock(String chave, Supplier<T> operacao) {
        String token = UUID.randomUUID().toString();
        Aquisicao aquisicao = adquirir(chave, token);

        if (aquisicao == Aquisicao.NAO_OBTIDO) {
            naoAdquiridos.increment();
            throw new LockIndisponivelException(chave, propriedades.tentativas());
        }

        try {
            return operacao.get();
        } finally {
            // So libera o que de fato adquiriu. Em modo degradado nunca houve chave no Redis, e
            // tentar apagar poderia atingir o lock de outra instancia que ainda alcance o Redis.
            if (aquisicao == Aquisicao.OBTIDO) {
                liberar(chave, token);
            }
        }
    }

    private Aquisicao adquirir(String chave, String token) {
        for (int tentativa = 1; tentativa <= propriedades.tentativas(); tentativa++) {
            try {
                Boolean obteve = redis.opsForValue()
                        .setIfAbsent(chave, token, propriedades.ttl());

                if (Boolean.TRUE.equals(obteve)) {
                    adquiridos.increment();
                    return Aquisicao.OBTIDO;
                }
            } catch (DataAccessException falhaDoRedis) {
                return degradar(chave, falhaDoRedis);
            }

            if (tentativa < propriedades.tentativas()) {
                aguardar(chave, tentativa);
            }
        }

        return Aquisicao.NAO_OBTIDO;
    }

    /**
     * Redis fora do ar: segue sem lock, em vez de recusar a reserva.
     *
     * <p>A alternativa seria devolver {@code 503}, e foi descartada de proposito. Recusar
     * reservas porque o Redis caiu transformaria o cache em ponto unico de falha da operacao
     * mais importante do sistema — e contradiria a propria tese do projeto, que e a de que a
     * garantia mora no banco. Sem lock a reserva continua correta: o {@code UPDATE} condicional
     * segue recusando qualquer tentativa que estoure a capacidade. O que se perde e desempenho,
     * porque a disputa passa a ser resolvida no banco, com mais transacoes concorrentes
     * chegando la e mais respostas {@code 409 SOLD_OUT} por corrida perdida.
     *
     * <p>Por isso o WARN e a metrica: degradacao silenciosa e o pior dos mundos, ja que o
     * sistema continua correto e ninguem descobre que o Redis morreu.
     */
    private Aquisicao degradar(String chave, DataAccessException causa) {
        degradacoes.increment();
        log.warn("Redis indisponivel ao adquirir o lock '{}'. Seguindo SEM lock: a garantia "
                        + "contra overselling continua no UPDATE condicional do banco, mas a "
                        + "contencao aumenta. Causa: {}",
                chave, causa.getMessage());

        return Aquisicao.SERVICO_INDISPONIVEL;
    }

    private void liberar(String chave, String token) {
        try {
            Long removidos = redis.execute(LIBERACAO_SEGURA, List.of(chave), token);

            if (removidos == null || removidos == 0) {
                expiradosNaSecaoCritica.increment();
                log.warn("o lock '{}' ja nao era nosso na hora de liberar: o TTL de {} venceu "
                                + "durante a operacao. Nenhuma reserva invalida decorre disso, "
                                + "mas indica TTL curto demais para a carga atual.",
                        chave, propriedades.ttl());
            }
        } catch (DataAccessException falhaDoRedis) {
            // A operacao ja terminou; falhar aqui nao a invalida. A chave sai sozinha pelo TTL.
            log.warn("falha ao liberar o lock '{}'; expirara pelo TTL em {}. Causa: {}",
                    chave, propriedades.ttl(), falhaDoRedis.getMessage());
        }
    }

    /**
     * Espera crescente com jitter antes da proxima tentativa.
     *
     * <p>O jitter nao e detalhe cosmetico: sem ele, todas as threads que perderam a mesma
     * disputa dormiriam o mesmo tempo, acordariam juntas e voltariam a colidir em bloco na
     * tentativa seguinte. Espalhar os despertares transforma uma sequencia de colisoes em
     * tentativas escalonadas.
     */
    private void aguardar(String chave, int tentativa) {
        long base = propriedades.esperaEntreTentativas().toMillis() * tentativa;
        long espera = base + ThreadLocalRandom.current().nextLong(base / 2 + 1);

        try {
            Thread.sleep(espera);
        } catch (InterruptedException interrupcao) {
            // Restaura o sinal para quem esta acima na pilha: engolir a interrupcao deixaria a
            // thread ignorando um pedido legitimo de parada.
            Thread.currentThread().interrupt();
            throw new LockIndisponivelException(chave, tentativa);
        }
    }

    /** Resultado da tentativa de aquisicao, que tem tres desfechos e nao dois. */
    private enum Aquisicao {
        /** Lock obtido; precisa ser liberado ao final. */
        OBTIDO,
        /** Outro processo o detem; a operacao nao deve prosseguir. */
        NAO_OBTIDO,
        /** Redis fora do ar; a operacao prossegue sem lock, protegida apenas pelo banco. */
        SERVICO_INDISPONIVEL
    }
}
