package com.devbandeiraa.apigateway.web;

import com.devbandeiraa.apigateway.correlacao.CorrelacaoFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Da corpo ao 429 do rate limiter, que por padrao sai sem nenhum.
 *
 * <p>O {@code RequestRateLimiter} recusa a requisicao definindo o status e chamando
 * {@code setComplete()} — resposta vazia. O frontend receberia um corpo em branco justamente no
 * erro em que precisa explicar algo ao usuario, e teria que tratar o 429 fora do caminho comum de
 * erros.
 *
 * <p>Interceptar exige decorar a resposta <em>antes</em> do rate limiter agir, porque depois dele
 * ela ja foi encerrada e nao ha mais onde escrever. Por isso um filtro global de ordem baixa
 * substituindo a resposta, e nao um tratador de excecao: recusa por limite nao lanca excecao
 * alguma, e portanto nenhum tratador seria chamado.
 */
@Component
public class CorpoDeLimiteExcedidoFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(CorpoDeLimiteExcedidoFilter.class);

    private final EscritorDeErro escritorDeErro;

    public CorpoDeLimiteExcedidoFilter(EscritorDeErro escritorDeErro) {
        this.escritorDeErro = escritorDeErro;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        ServerHttpResponse original = troca.getResponse();

        ServerHttpResponse decorada = new ServerHttpResponseDecorator(original) {

            @Override
            public Mono<Void> setComplete() {
                if (!HttpStatus.TOO_MANY_REQUESTS.equals(getStatusCode()) || isCommitted()) {
                    return super.setComplete();
                }

                String traceId = CorrelacaoFilter.idDa(troca);
                log.warn("traceId={} limite excedido em {}",
                        traceId, troca.getRequest().getPath().value());

                getHeaders().setContentType(MediaType.APPLICATION_JSON);

                // super.writeWith, e nao o EscritorDeErro: escrever pela resposta decorada
                // reentraria neste mesmo metodo. Os cabecalhos que o rate limiter ja pos —
                // X-RateLimit-Remaining e companhia — sao preservados por escrever na mesma
                // resposta, em vez de montar outra.
                return super.writeWith(Mono.fromSupplier(() -> escritorDeErro.serializar(
                        troca,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "Muitas requisicoes. Tente novamente em alguns instantes",
                        traceId,
                        bufferFactory())));
            }
        };

        return cadeia.filter(troca.mutate().response(decorada).build());
    }

    /**
     * Logo depois da autenticacao e bem antes dos filtros de rota, que e onde o rate limiter roda.
     * A decoracao precisa estar de pe quando ele decidir recusar.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
