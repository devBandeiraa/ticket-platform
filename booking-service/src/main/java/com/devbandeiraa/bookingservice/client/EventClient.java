package com.devbandeiraa.bookingservice.client;

import com.devbandeiraa.bookingservice.exception.EventServiceIndisponivelException;
import com.devbandeiraa.bookingservice.exception.EventoNaoDisponivelException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Le do event-service os dados de um evento.
 *
 * <p>Usa o endpoint publico {@code GET /events/{id}} em vez do {@code /internal/events/{id}} que
 * o mapeamento previa. O endpoint interno nao se justificou: o publico ja devolve capacidade e
 * preco, e ja filtra por eventos publicados — que e exatamente a regra desejada, porque nao se
 * vende ingresso de rascunho. Um endpoint a mais entregaria os mesmos dados sob outra URL, com
 * mais superficie para manter em sincronia.
 *
 * <p>Chamado uma unica vez por evento, na hidratacao do estoque local. Depois disso as reservas
 * daquele evento nao dependem mais do event-service estar no ar.
 */
@Component
public class EventClient {

    private static final Logger log = LoggerFactory.getLogger(EventClient.class);

    /**
     * Nome da instancia configurada em {@code resilience4j.circuitbreaker.instances}, e tambem o
     * nome sob o qual o estado aparece em {@code /actuator/circuitbreakers}.
     */
    public static final String INSTANCIA = "event-service";

    private final RestClient restClient;

    public EventClient(RestClient eventRestClient) {
        this.restClient = eventRestClient;
    }

    /**
     * Busca um evento publicado.
     *
     * <h2>O que o circuit breaker conta como falha</h2>
     *
     * <p>Um {@code 404} <strong>nao</strong> abre o circuito, e essa e a configuracao mais
     * importante desta classe. Evento inexistente significa que o event-service esta perfeitamente
     * saudavel e respondeu depressa; contar isso como falha faria um punhado de usuarios digitando
     * ids errados derrubar a hidratacao de todos os eventos legitimos. So
     * {@link EventServiceIndisponivelException} — timeout, conexao recusada, 5xx — conta.
     *
     * @throws EventoNaoDisponivelException      se o evento nao existe ou nao esta publicado
     * @throws EventServiceIndisponivelException se o event-service nao respondeu, ou se o circuito
     *                                           esta aberto
     */
    @CircuitBreaker(name = INSTANCIA, fallbackMethod = "circuitoAberto")
    public EventSnapshot buscarPublicado(UUID eventId) {
        try {
            EventSnapshot evento = restClient.get()
                    .uri("/events/{id}", eventId)
                    .retrieve()
                    .body(EventSnapshot.class);

            if (evento == null) {
                throw new EventoNaoDisponivelException(eventId);
            }

            log.debug("evento {} obtido do event-service: capacidade={} preco={}",
                    eventId, evento.totalTickets(), evento.price());

            return evento;

        } catch (HttpClientErrorException.NotFound naoEncontrado) {
            // O event-service devolve 404 tanto para evento inexistente quanto para rascunho ou
            // cancelado. Do ponto de vista de quem reserva, os tres casos sao o mesmo: nao ha o
            // que comprar. Distingui-los aqui revelaria a existencia de eventos nao publicados.
            throw new EventoNaoDisponivelException(eventId);

        } catch (RestClientException falhaDeComunicacao) {
            // Timeout, conexao recusada, 5xx. Diferente do 404, aqui nao se sabe se o evento
            // existe — e responder "nao existe" quando a verdade e "nao consegui perguntar"
            // levaria o usuario a desistir de um evento que esta a venda.
            throw new EventServiceIndisponivelException(eventId, falhaDeComunicacao);
        }
    }

    /**
     * Circuito aberto: falha imediata, sem chamar o event-service.
     *
     * <h2>Por que o fallback nao inventa um estoque</h2>
     *
     * <p>A tentacao num fallback e devolver algo plausivel para a reserva seguir — uma capacidade
     * padrao, um valor em cache. Aqui isso seria desastroso: este metodo alimenta exatamente o
     * numero contra o qual o {@code UPDATE} condicional compara para nao vender ingresso a mais.
     * Um chute de capacidade produziria overselling <em>de verdade</em>, e a plataforma inteira
     * existe para nao fazer isso.
     *
     * <p>Entao o fallback aqui e recusar depressa. O ganho do circuit breaker nao e continuar
     * atendendo: e parar de gastar threads e timeouts contra um servico que ja se sabe fora, e
     * responder em milissegundos em vez de segundos.
     *
     * <p>O alcance disso e menor do que parece, e vale reparar: a hidratacao acontece <b>uma vez
     * por evento</b>. Com o circuito aberto, os eventos ja hidratados continuam vendendo
     * normalmente — so a primeira reserva de um evento novo e recusada. A degradacao e parcial,
     * que e o que se espera de um sistema resiliente.
     */
    @SuppressWarnings("unused") // invocado por reflexao pelo Resilience4j
    private EventSnapshot circuitoAberto(UUID eventId, CallNotPermittedException circuitoAberto) {
        log.warn("circuito aberto para o event-service: evento {} recusado sem tentar a chamada",
                eventId);

        throw new EventServiceIndisponivelException(eventId, circuitoAberto);
    }
}
