package com.devbandeiraa.bookingservice.messaging;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Serializa e restaura o contexto de trace que atravessa a outbox.
 *
 * <h2>O problema</h2>
 *
 * <p>A outbox e, por construcao, uma quebra na linha do tempo. A mensagem e gravada dentro da
 * transacao da reserva; a publicacao acontece segundos depois, numa thread de job que nao sabe
 * nada daquela requisicao. Como o contexto de trace vive no escopo da thread, ele simplesmente
 * nao esta la na hora de publicar.
 *
 * <p>O resultado, sem tratamento, e um Jaeger que mostra duas arvores desconexas: uma da compra e
 * outra da notificacao. As duas existem, e nada diz que a segunda aconteceu por causa da primeira
 * — que e exatamente a pergunta que leva alguem a abrir o Jaeger.
 *
 * <h2>A solucao</h2>
 *
 * <p>Guardar o contexto junto da mensagem, na mesma linha e na mesma transacao, e restaura-lo na
 * publicacao. O span do publicador passa a ser filho do span da requisicao original, e o do
 * consumidor, neto — apesar dos segundos de intervalo e da troca de processo.
 *
 * <p>A serializacao usa o {@link Propagator} configurado, em vez de montar o cabecalho na mao.
 * Trocar o formato de propagacao — de W3C para B3, por exemplo — passa a ser configuracao, sem
 * mexer nesta classe.
 */
@Component
public class ContextoDeTrace {

    /**
     * Campo do W3C Trace Context, que e o formato padrao do Spring Boot.
     *
     * <p>Guarda-se apenas ele, e nao o portador inteiro: uma coluna simples e legivel vale mais
     * que um JSON de cabecalhos para o unico campo que carrega a arvore. O {@code tracestate},
     * que o W3C tambem preve, transporta metadados de fornecedor que nao existem aqui.
     */
    private static final String CAMPO_W3C = "traceparent";

    /** Nome que aparece no Jaeger. Curto e sem id: o que varia vai em atributo, nao no nome. */
    private static final String NOME_DO_SPAN = "outbox publicar";

    private final Tracer tracer;
    private final Propagator propagator;

    public ContextoDeTrace(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /**
     * Contexto de trace atual em texto, ou {@code null} quando nao ha trace ativo.
     *
     * <p>O nulo nao e falha: um evento registrado por um job, sem requisicao HTTP na origem, nao
     * tem contexto a guardar. Quem publica trata esse caso publicando normalmente.
     */
    public String capturar() {
        Span atual = tracer.currentSpan();
        if (atual == null) {
            return null;
        }

        Map<String, String> portador = new HashMap<>();
        propagator.inject(atual.context(), portador, Map::put);

        return portador.get(CAMPO_W3C);
    }

    /**
     * Abre um span filho do contexto guardado, para a publicacao acontecer dentro dele.
     *
     * <p>Devolve um span sem pai quando nao ha contexto — a publicacao continua sendo registrada,
     * apenas como raiz da propria arvore. Perder a ligacao e ruim; perder o span inteiro seria
     * pior, porque a mensagem sumiria da observabilidade em vez de aparecer desconectada.
     */
    public Span.Builder abrirPublicacao(String traceParent) {
        if (traceParent == null || traceParent.isBlank()) {
            return tracer.spanBuilder().name(NOME_DO_SPAN);
        }

        Map<String, String> portador = Map.of(CAMPO_W3C, traceParent);

        // `extract` devolve um construtor ja com o pai definido, e nao um span pronto: e o que
        // permite nomear e iniciar o span aqui, do lado de ca da fronteira.
        return propagator.extract(portador, Map::get).name(NOME_DO_SPAN);
    }

    /** Torna o span o contexto corrente, para o RabbitTemplate injeta-lo na mensagem. */
    public Tracer.SpanInScope escopoDe(Span span) {
        return tracer.withSpan(span);
    }
}
