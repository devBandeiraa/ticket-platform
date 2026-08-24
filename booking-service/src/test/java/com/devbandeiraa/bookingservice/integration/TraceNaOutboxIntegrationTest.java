package com.devbandeiraa.bookingservice.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.devbandeiraa.bookingservice.domain.OutboxMessage;
import com.devbandeiraa.bookingservice.messaging.BookingConfirmedEvent;
import com.devbandeiraa.bookingservice.messaging.ContextoDeTrace;
import com.devbandeiraa.bookingservice.messaging.OutboxRegistrar;
import com.devbandeiraa.bookingservice.repository.OutboxRepository;
import com.devbandeiraa.bookingservice.support.TestcontainersConfig;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * A continuidade do trace atraves da outbox.
 *
 * <p>A outbox e uma quebra na linha do tempo: a mensagem e gravada dentro da transacao da reserva
 * e publicada segundos depois, por um job, numa thread sem relacao nenhuma com aquela requisicao.
 * O que se verifica aqui e que o contexto sobrevive a essa travessia.
 *
 * <p>Este teste cobre a metade gravavel do problema — que o contexto seja capturado e persistido.
 * A outra metade, o span do consumidor aparecer pendurado na arvore certa, so se confirma olhando
 * o Jaeger; esta no roteiro de verificacao manual.
 *
 * <p><strong>{@code @AutoConfigureObservability} nao e decoracao.</strong> O Spring Boot desliga
 * tracing e exportacao de metricas em teste por padrao, e a razao e boa: sem isso, cada execucao da
 * suite despejaria spans e metricas num backend real. O efeito colateral e que o propagador W3C nao
 * e registrado — no lugar dele entra um {@code NoopTextMapPropagator}, cujo {@code inject} escreve
 * num mapa vazio sem reclamar. Um teste que verifica propagacao precisa, portanto, religar
 * explicitamente aquilo que ele existe para testar.
 */
@SpringBootTest
@AutoConfigureObservability
@Import(TestcontainersConfig.class)
@ActiveProfiles("test")
class TraceNaOutboxIntegrationTest {

    @Autowired
    private OutboxRegistrar registrar;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ContextoDeTrace contextoDeTrace;

    @Autowired
    private Tracer tracer;

    @BeforeEach
    void limparEstado() {
        outboxRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("o evento guarda o contexto de trace da requisicao que o originou")
    void deveGuardarOContextoDaRequisicao() {
        Span requisicao = tracer.nextSpan().name("pagamento de mentira").start();

        String traceIdEsperado;
        try (Tracer.SpanInScope ignorado = tracer.withSpan(requisicao)) {
            traceIdEsperado = requisicao.context().traceId();
            registrar.registrarConfirmacao(eventoDeConfirmacao());
        } finally {
            requisicao.end();
        }

        List<OutboxMessage> gravadas = outboxRepository.findAll();
        assertThat(gravadas).hasSize(1);

        String traceParent = gravadas.get(0).getTraceParent();

        // Formato W3C: 00-<32 hex>-<16 hex>-<flags>. O traceId precisa ser o MESMO da requisicao,
        // senao a publicacao penduraria a notificacao numa arvore que nao e a da compra.
        assertThat(traceParent)
                .as("sem o contexto guardado, o consumidor comeca uma arvore solta no Jaeger")
                .isNotNull()
                .matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
                .contains(traceIdEsperado);
    }

    @Test
    @DisplayName("evento registrado sem trace ativo e gravado assim mesmo")
    void deveGravarSemContextoQuandoNaoHaTrace() {
        // Acontece de verdade: um evento originado por um job nao tem requisicao HTTP na origem.
        // Recusar a gravacao por falta de contexto trocaria um problema de observabilidade por um
        // de perda de mensagem, que e incomparavelmente pior.
        registrar.registrarConfirmacao(eventoDeConfirmacao());

        List<OutboxMessage> gravadas = outboxRepository.findAll();
        assertThat(gravadas).hasSize(1);
        assertThat(gravadas.get(0).getTraceParent()).isNull();
    }

    @Test
    @DisplayName("sem contexto guardado, a publicacao ainda abre um span proprio")
    void deveAbrirSpanMesmoSemContexto() {
        // Perder a ligacao e ruim; perder o span inteiro seria pior, porque a mensagem sumiria da
        // observabilidade em vez de aparecer desconectada.
        Span publicacao = contextoDeTrace.abrirPublicacao(null).start();

        try {
            assertThat(publicacao.context().traceId()).isNotBlank();
        } finally {
            publicacao.end();
        }
    }

    private BookingConfirmedEvent eventoDeConfirmacao() {
        return new BookingConfirmedEvent(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                2, new BigDecimal("300.00"), Instant.now());
    }
}
