package com.devbandeiraa.bookingservice.metrics;

import com.devbandeiraa.bookingservice.domain.Booking;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * O que o negocio pergunta, e que as metricas de infraestrutura nao respondem.
 *
 * <p>Ate aqui o projeto media lock, outbox e deduplicacao — tudo sobre a mecanica interna. Sao
 * uteis num incidente, e inuteis na pergunta que alguem de fato faz primeiro: <em>vendeu?</em>
 *
 * <p>A distincao importa porque as duas falham de formas diferentes. A plataforma pode estar com
 * todos os indicadores tecnicos verdes — sem erro, sem fila, latencia baixa — e nao vender
 * ingresso nenhum, porque o catalogo subiu vazio ou o pagamento esta recusando tudo. Nenhum
 * painel de infraestrutura mostra isso; um contador de lugares vendidos mostra na hora.
 *
 * <p>Separado num componente proprio, e nao espalhado pelos services, por dois motivos. A
 * instrumentacao nao e regra de negocio, e misturar as duas faz o service crescer com assunto
 * alheio; e concentrar os nomes das metricas num lugar so evita que um painel do Grafana dependa
 * de uma string escrita no meio de um metodo — que e como um dashboard quebra em silencio.
 */
@Component
public class MetricasDeNegocio {

    private final Counter lugaresVendidos;
    private final Counter reservasConfirmadas;
    private final Counter reservasExpiradas;
    private final Counter reservasCanceladas;
    private final Timer tempoAteAConfirmacao;
    private final Counter lugaresLiberadosPorExpiracao;
    private final Counter lugaresLiberadosPorCancelamento;

    public MetricasDeNegocio(MeterRegistry metricas) {
        // Lugares, e nao reservas: uma reserva de quatro ingressos vale quatro na contagem de
        // ocupacao da casa. Contar so reservas faria um evento parecer vazio quando todas as
        // compras sao em grupo.
        //
        // Sem `baseUnit`: o Prometheus ANEXA a unidade ao nome, e o resultado era
        // `booking_lugares_vendidos_lugares_total` — a palavra duas vezes. O nome ja diz o que
        // conta, e um painel que consulta por nome nao ganha nada com a repeticao.
        this.lugaresVendidos = Counter.builder("booking.lugares.vendidos")
                .description("Lugares efetivamente pagos")
                .register(metricas);

        this.reservasConfirmadas = Counter.builder("booking.reservas.confirmadas")
                .description("Reservas que chegaram ao pagamento")
                .register(metricas);

        // Expiradas e canceladas sao contadas em separado de proposito: a primeira mede
        // desistencia por inercia — a pessoa reservou e sumiu —, e a segunda, desistencia
        // deliberada. Somadas, escondem justamente a diferenca que diria se o prazo de
        // pagamento esta curto demais.
        this.reservasExpiradas = Counter.builder("booking.reservas.expiradas")
                .description("Reservas que venceram o prazo sem pagamento")
                .register(metricas);

        this.reservasCanceladas = Counter.builder("booking.reservas.canceladas")
                .description("Reservas desfeitas pelo proprio usuario")
                .register(metricas);

        // Lugares que voltaram ao pool, com o motivo como etiqueta. Dois valores possiveis, e
        // portanto cardinalidade irrisoria — o que se ganha e poder somar os dois num painel e
        // ainda assim separa-los quando a pergunta for "por que a casa nao enche?".
        this.lugaresLiberadosPorExpiracao = lugaresLiberados(metricas, "expiracao");
        this.lugaresLiberadosPorCancelamento = lugaresLiberados(metricas, "cancelamento");

        this.tempoAteAConfirmacao = Timer.builder("booking.tempo.ate.confirmacao")
                .description("Da criacao da reserva ate o pagamento")
                // Faixas escolhidas em torno do TTL da reserva: o que interessa e saber quantas
                // pessoas pagam logo e quantas deixam para o fim do prazo. Um histograma sem
                // limites uteis gera cardinalidade sem responder pergunta alguma.
                .serviceLevelObjectives(
                        Duration.ofSeconds(30), Duration.ofMinutes(1),
                        Duration.ofMinutes(5), Duration.ofMinutes(10))
                .register(metricas);
    }

    /**
     * Registra o pagamento de uma reserva.
     *
     * <p>O tempo medido vai da <strong>criacao</strong> ao pagamento, e nao da chamada ao
     * provedor: o que se quer saber e quanto o usuario demora para decidir, nao quanto a
     * cobranca demora para responder — essa ja aparece na latencia HTTP do payment-simulator.
     */
    public void confirmada(Booking reserva) {
        reservasConfirmadas.increment();
        lugaresVendidos.increment(reserva.getQuantity());

        if (reserva.getPaidAt() != null) {
            tempoAteAConfirmacao.record(
                    Duration.between(reserva.getCreatedAt(), reserva.getPaidAt()));
        }
    }

    /** Mesma metrica, etiquetas diferentes: somam num painel e separam quando a pergunta muda. */
    private static Counter lugaresLiberados(MeterRegistry metricas, String motivo) {
        return Counter.builder("booking.lugares.liberados")
                .description("Lugares que voltaram a ficar livres")
                .tag("motivo", motivo)
                .register(metricas);
    }

    /** Reserva que venceu o prazo sem pagamento: desistencia por inercia. */
    public void expirada(int lugares) {
        reservasExpiradas.increment();
        lugaresLiberadosPorExpiracao.increment(lugares);
    }

    /** Reserva desfeita pelo proprio usuario: desistencia deliberada. */
    public void cancelada(int lugares) {
        reservasCanceladas.increment();
        lugaresLiberadosPorCancelamento.increment(lugares);
    }
}
