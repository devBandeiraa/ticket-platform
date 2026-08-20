package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.devbandeiraa.bookingservice.lock.DistributedLock;
import com.devbandeiraa.bookingservice.lock.LockIndisponivelException;
import com.devbandeiraa.bookingservice.lock.LockProperties;
import com.devbandeiraa.bookingservice.lock.RedisDistributedLock;
import com.devbandeiraa.bookingservice.support.PostgresContainerConfig;
import com.devbandeiraa.bookingservice.support.RedisContainerConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Testes do lock contra um Redis real.
 *
 * <p>Verificam as duas propriedades que so o servidor pode garantir: que dois processos nao
 * entram juntos na secao critica, e que liberar o lock nunca atinge o lock de outro.
 */
@SpringBootTest
@Import({PostgresContainerConfig.class, RedisContainerConfig.class})
@ActiveProfiles("test")
class LockDistribuidoIntegrationTest {

    @Autowired
    private DistributedLock lock;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    @DisplayName("enquanto um processo detem o lock, o outro nao entra")
    void deveExcluirMutuamente() throws Exception {
        String chave = chaveNova();
        CountDownLatch dentroDaSecaoCritica = new CountDownLatch(1);
        CountDownLatch podeSair = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<String> primeiro = executor.submit(() -> lock.executarComLock(chave, () -> {
                dentroDaSecaoCritica.countDown();
                aguardar(podeSair);
                return "primeiro";
            }));

            assertThat(dentroDaSecaoCritica.await(5, TimeUnit.SECONDS)).isTrue();

            // O primeiro esta segurando o lock neste exato instante.
            assertThatThrownBy(() -> lock.executarComLock(chave, () -> "segundo"))
                    .isInstanceOf(LockIndisponivelException.class);

            podeSair.countDown();
            assertThat(primeiro.get(5, TimeUnit.SECONDS)).isEqualTo("primeiro");

            // Liberado o lock, o caminho volta a ficar aberto.
            assertThat(lock.executarComLock(chave, () -> "terceiro")).isEqualTo("terceiro");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("liberar o lock nunca apaga o lock de outro processo")
    void naoDeveApagarLockAlheio() {
        String chave = chaveNova();
        String tokenDoOutroProcesso = "token-de-outro-processo";

        // TTL curto de proposito: e a unica forma de reproduzir, em tempo de teste, o cenario em
        // que a secao critica dura mais que o lock.
        DistributedLock lockDeTtlCurto = new RedisDistributedLock(
                redis,
                new LockProperties(Duration.ofMillis(200), 1, Duration.ZERO),
                new SimpleMeterRegistry());

        lockDeTtlCurto.executarComLock(chave, () -> {
            // A secao critica passa do TTL e a chave expira sozinha...
            dormir(400);
            // ...e outro processo, legitimamente, adquire o lock que ficou livre.
            redis.opsForValue().set(chave, tokenDoOutroProcesso, Duration.ofSeconds(30));
            return null;
        });

        // Ao sair, o primeiro processo tentou liberar. Com um DEL cego teria apagado o lock do
        // segundo, que seguiria se achando dono de algo que ja nao existe — e um terceiro
        // processo entraria junto com ele.
        assertThat(redis.opsForValue().get(chave)).isEqualTo(tokenDoOutroProcesso);
    }

    @Test
    @DisplayName("a chave e removida ao final, e nao deixada para o TTL")
    void deveRemoverAChaveAoFinal() {
        String chave = chaveNova();

        lock.executarComLock(chave, () -> "feito");

        // Deixar a chave viva ate o TTL bloquearia por segundos as reservas seguintes do mesmo
        // evento — exatamente o oposto do que o lock existe para fazer.
        assertThat(redis.hasKey(chave)).isFalse();
    }

    /** Chave nova por teste: os casos nao podem interferir entre si. */
    private String chaveNova() {
        return "lock:event:" + UUID.randomUUID();
    }

    private void aguardar(CountDownLatch trava) {
        try {
            trava.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupcao) {
            Thread.currentThread().interrupt();
        }
    }

    private void dormir(long milissegundos) {
        try {
            Thread.sleep(milissegundos);
        } catch (InterruptedException interrupcao) {
            Thread.currentThread().interrupt();
        }
    }
}
