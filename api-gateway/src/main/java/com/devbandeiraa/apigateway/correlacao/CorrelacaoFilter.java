package com.devbandeiraa.apigateway.correlacao;

import com.devbandeiraa.shared.security.CorrelacaoDeRequisicao;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Garante que toda requisicao carregue um {@code X-Request-Id} ao seguir para os servicos.
 *
 * <p>E a origem da corrente de correlacao. O gateway e o unico ponto por onde todo o trafego
 * externo passa, entao e o unico lugar onde se pode afirmar que <em>nenhuma</em> requisicao segue
 * adiante sem identificacao — atribuir o id em cada servico deixaria de fora justamente as
 * requisicoes recusadas na borda.
 *
 * <p>Nao ha MDC aqui, ao contrario do filtro de servlet dos servicos. Em WebFlux uma requisicao
 * nao pertence a uma thread: ela salta entre as threads da event loop conforme os estagios
 * assincronos completam, e o MDC vive numa ThreadLocal — o id gravado num estagio simplesmente nao
 * estaria la no seguinte, e pior, poderia estar o de outra requisicao. Por isso o id fica num
 * atributo da troca, que acompanha a requisicao aonde quer que ela va, e os filtros que registram
 * log o leem de {@link #idDa(ServerWebExchange)}.
 */
@Component
public class CorrelacaoFilter implements GlobalFilter, Ordered {

    /**
     * Nome qualificado como chave para nao colidir com atributos do proprio Spring Cloud Gateway,
     * que guarda os dele na mesma estrutura.
     */
    private static final String ATRIBUTO = CorrelacaoFilter.class.getName() + ".id";

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        String id = CorrelacaoDeRequisicao.normalizar(
                troca.getRequest().getHeaders().getFirst(CorrelacaoDeRequisicao.CABECALHO));

        troca.getAttributes().put(ATRIBUTO, id);

        // beforeCommit, e nao escrita direta no cabecalho: a resposta do servico de destino tambem
        // traz o seu X-Request-Id, e o gateway copia os cabecalhos recebidos para a resposta final.
        // Escrevendo agora, o valor seria somado ao de la e o cliente receberia o cabecalho
        // duplicado. Sobrescrevendo no ultimo instante antes do envio, sai exatamente um.
        troca.getResponse().beforeCommit(() -> {
            troca.getResponse().getHeaders().set(CorrelacaoDeRequisicao.CABECALHO, id);
            return Mono.empty();
        });

        // `set` e nao `add`: um cliente que mandou um cabecalho fora do formato aceito teve o valor
        // descartado por CorrelacaoDeRequisicao, e deixar o original junto do novo faria o servico
        // de destino ler o primeiro da lista — que seria justamente o valor recusado.
        ServerWebExchange comCorrelacao = troca.mutate()
                .request(requisicao -> requisicao.headers(
                        cabecalhos -> cabecalhos.set(CorrelacaoDeRequisicao.CABECALHO, id)))
                .build();

        return cadeia.filter(comCorrelacao);
    }

    /**
     * Id desta requisicao, para quem precisa registra-lo em log ou devolve-lo num corpo de erro.
     *
     * <p>O sorteio no caminho de fuga so seria alcancado se este filtro nao tivesse rodado, o que
     * a ordem abaixo torna impossivel em producao. Ele existe para nao obrigar cada chamador a
     * tratar um nulo que nunca vem.
     */
    public static String idDa(ServerWebExchange troca) {
        return troca.getAttribute(ATRIBUTO) instanceof String id ? id : CorrelacaoDeRequisicao.gerar();
    }

    /**
     * Primeiro de todos, sem excecao.
     *
     * <p>Os demais filtros globais respondem por conta propria em alguns casos — token invalido,
     * limite excedido — e precisam do id ja disponivel para registra-lo. Fosse este filtro o
     * segundo, as unicas requisicoes sem correlacao seriam as recusadas, ou seja, exatamente as
     * que alguem vai querer investigar.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
