package com.devbandeiraa.bookingservice.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.devbandeiraa.bookingservice.lock.LockIndisponivelException;
import com.devbandeiraa.bookingservice.lock.LockProperties;
import com.devbandeiraa.bookingservice.lock.RedisDistributedLock;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Testes do lock sem Redis.
 *
 * <p>Os cenarios que mais importam aqui sao justamente os dificeis de provocar contra um Redis
 * de verdade: o servidor cair no instante da aquisicao, e o TTL vencer no meio da secao critica.
 * Com o cliente simulado eles viram casos deterministicos, executados em milissegundos.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedisDistributedLockTest {

    private static final String CHAVE = "lock:event:abc";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> operacoesDeValor;

    private RedisDistributedLock lock;

    @BeforeEach
    void preparar() {
        when(redis.opsForValue()).thenReturn(operacoesDeValor);

        // Espera zero: os testes verificam a decisao tomada, nao o tempo gasto esperando.
        lock = new RedisDistributedLock(
                redis,
                new LockProperties(Duration.ofSeconds(3), 3, Duration.ZERO),
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("com o lock obtido, executa a operacao e libera ao final")
    void deveExecutarELiberar() {
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenReturn(true);

        String resultado = lock.executarComLock(CHAVE, () -> "feito");

        assertThat(resultado).isEqualTo("feito");
        verify(redis).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("esgotadas as tentativas, recusa a operacao em vez de executa-la sem lock")
    void deveRecusarQuandoOutroDetemOLock() {
        // O lock existe e continua de outro processo em todas as tentativas.
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenReturn(false);
        AtomicBoolean executou = new AtomicBoolean(false);

        assertThatThrownBy(() -> lock.executarComLock(CHAVE, () -> {
            executou.set(true);
            return "nao deveria";
        })).isInstanceOf(LockIndisponivelException.class);

        assertThat(executou).isFalse();
        verify(operacoesDeValor, times(3)).setIfAbsent(eq(CHAVE), anyString(), any(Duration.class));
        // Nao adquiriu, entao nao pode liberar: um DEL aqui apagaria o lock do dono legitimo.
        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("adquire na segunda tentativa quando o lock e liberado no meio do caminho")
    void deveReTentar() {
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenReturn(false, true);

        assertThat(lock.executarComLock(CHAVE, () -> "feito")).isEqualTo("feito");
        verify(operacoesDeValor, times(2)).setIfAbsent(eq(CHAVE), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis fora do ar: executa mesmo assim, porque a garantia esta no banco")
    void deveDegradarQuandoRedisCai() {
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        // Esta e a decisao de arquitetura sendo verificada, e nao um detalhe de implementacao:
        // o servico segue reservando sem o Redis. Recusar aqui faria do cache um ponto unico
        // de falha da operacao mais importante do sistema.
        assertThat(lock.executarComLock(CHAVE, () -> "feito")).isEqualTo("feito");

        // Uma so tentativa: se o Redis esta fora, insistir tres vezes so adiciona latencia.
        verify(operacoesDeValor, times(1)).setIfAbsent(eq(CHAVE), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("em modo degradado nao tenta liberar lock que nunca chegou a existir")
    void naoDeveLiberarEmModoDegradado() {
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        lock.executarComLock(CHAVE, () -> "feito");

        verify(redis, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("falha ao liberar nao derruba a operacao ja concluida")
    void deveTolerarFalhaNaLiberacao() {
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("caiu depois de adquirir"));

        // A reserva ja foi gravada quando a liberacao acontece. Propagar esta falha faria o
        // usuario receber erro por uma operacao que deu certo; a chave sai sozinha pelo TTL.
        assertThat(lock.executarComLock(CHAVE, () -> "feito")).isEqualTo("feito");
    }

    @Test
    @DisplayName("a operacao propaga suas proprias excecoes, e o lock ainda assim e liberado")
    void deveLiberarMesmoComFalhaNaOperacao() {
        when(operacoesDeValor.setIfAbsent(eq(CHAVE), anyString(), any(Duration.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> lock.executarComLock(CHAVE, () -> {
            throw new IllegalStateException("falha no meio da reserva");
        })).isInstanceOf(IllegalStateException.class);

        // Sem isso, um erro de negocio deixaria o evento travado ate o TTL vencer.
        verify(redis).execute(any(RedisScript.class), anyList(), anyString());
    }
}
