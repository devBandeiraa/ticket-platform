package com.devbandeiraa.apigateway.security;

import com.devbandeiraa.apigateway.web.EscritorDeErro;
import com.devbandeiraa.shared.security.AuthenticatedUser;
import com.devbandeiraa.shared.security.JwtTokenReader;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Autentica na borda: valida o access token e afirma a identidade do chamador em cabecalhos.
 *
 * <h2>Autentica, mas nao autoriza</h2>
 *
 * <p>O gateway confere <em>quem</em> esta chamando; <em>o que</em> cada um pode fazer continua
 * decidido em cada servico. Replicar aqui as regras de rota criaria uma segunda copia delas, e
 * duas copias divergem: bastaria alguem adicionar um endpoint administrativo no event-service e
 * esquecer de espelhar a regra aqui para o gateway liberar o que o servico julgava protegido.
 *
 * <p>Por isso requisicao sem token <b>passa</b>: nao ha erro em nao se identificar, e quem sabe se
 * a rota exige identificacao e o servico de destino, que responde 401 quando for o caso. Ja um
 * token <b>invalido</b> para aqui com 401 — expirado, adulterado ou de outro emissor nao vira
 * valido em servico nenhum, e encaminha-lo so gastaria uma chamada de rede para receber a mesma
 * resposta.
 *
 * <h2>Por que validar duas vezes, entao</h2>
 *
 * <p>Os servicos seguem validando o JWT por conta propria, e isso nao e redundancia desperdicada:
 * e o que impede que alcancar um servico diretamente, por dentro da rede, contorne a autenticacao.
 * Um gateway cuja unica garantia e "ninguem fala com o backend sem passar por mim" transforma
 * qualquer brecha de rede em acesso irrestrito.
 *
 * <p>A validacao aqui tem funcao propria: recusa cedo o que ja se sabe invalido e, principalmente,
 * produz o {@code userId} que o rate limiter usa como chave.
 */
@Component
public class AutenticacaoNaBordaFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AutenticacaoNaBordaFilter.class);
    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtTokenReader leitorDeToken;
    private final EscritorDeErro escritorDeErro;

    public AutenticacaoNaBordaFilter(JwtTokenReader leitorDeToken, EscritorDeErro escritorDeErro) {
        this.leitorDeToken = leitorDeToken;
        this.escritorDeErro = escritorDeErro;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange troca, GatewayFilterChain cadeia) {
        String cabecalho = troca.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (cabecalho == null || !cabecalho.startsWith(PREFIXO_BEARER)) {
            return cadeia.filter(semIdentidade(troca));
        }

        try {
            AuthenticatedUser usuario =
                    leitorDeToken.extrairUsuario(cabecalho.substring(PREFIXO_BEARER.length()));
            return cadeia.filter(comIdentidade(troca, usuario));

        } catch (JwtException | IllegalArgumentException falha) {
            // IllegalArgumentException cobre o token bem assinado mas com claims impossiveis —
            // um `sub` que nao e UUID, um `role` que nao existe no enum.
            String traceId = EscritorDeErro.gerarTraceId();
            log.debug("traceId={} token recusado em {}: {}",
                    traceId, troca.getRequest().getPath().value(), falha.getMessage());

            return escritorDeErro.escrever(troca, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                    "Token ausente, expirado ou invalido", traceId);
        }
    }

    /** Requisicao anonima: os cabecalhos de identidade que o cliente tenha mandado sao apagados. */
    private ServerWebExchange semIdentidade(ServerWebExchange troca) {
        return troca.mutate()
                .request(requisicao -> requisicao.headers(
                        cabecalhos -> CabecalhosDeIdentidade.TODOS.forEach(cabecalhos::remove)))
                .build();
    }

    private ServerWebExchange comIdentidade(ServerWebExchange troca, AuthenticatedUser usuario) {
        return troca.mutate()
                .request(requisicao -> requisicao.headers(cabecalhos -> {
                    // A remocao vem antes da escrita: `set` ja sobrescreveria, mas depender disso
                    // deixaria a protecao a merce de alguem trocar `set` por `add` um dia.
                    CabecalhosDeIdentidade.TODOS.forEach(cabecalhos::remove);
                    cabecalhos.set(CabecalhosDeIdentidade.USER_ID, usuario.id().toString());
                    cabecalhos.set(CabecalhosDeIdentidade.USER_EMAIL, usuario.email());
                    cabecalhos.set(CabecalhosDeIdentidade.USER_ROLE, usuario.role().name());
                }))
                .build();
    }

    /**
     * Primeiro de todos. O rate limiter e um filtro de rota, portanto de ordem maior, e so assim
     * ele encontra o {@code X-User-Id} ja preenchido para usar como chave.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
