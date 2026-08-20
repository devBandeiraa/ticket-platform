package com.devbandeiraa.apigateway.ratelimit;

import com.devbandeiraa.apigateway.security.CabecalhosDeIdentidade;
import java.net.InetSocketAddress;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Define contra qual balde de fichas cada requisicao e contada.
 *
 * <p>Sao dois resolvedores porque o {@code RedisRateLimiter} monta a chave do Redis a partir do
 * valor devolvido aqui, e nao do identificador da rota. Dois limites com a mesma chave dividiriam
 * o mesmo balde, com taxas de reposicao conflitantes — o balde estrito do login seria reabastecido
 * pela taxa generosa das demais rotas e deixaria de limitar coisa alguma.
 */
@Configuration(proxyBeanMethods = false)
public class ChavesDeRateLimit {

    /**
     * Balde geral: por usuario quando autenticado, por IP quando anonimo.
     *
     * <p>Contar sempre por IP puniria colegas atras de um mesmo NAT — um escritorio inteiro
     * dividindo a cota de um. Depois do login existe identidade melhor que o endereco de rede, e e
     * ela que passa a valer.
     *
     * <p>{@code @Primary} porque o {@code RequestRateLimiterGatewayFilterFactory} injeta um
     * {@code KeyResolver} padrao para as rotas que nao indicarem qual usar, e com dois candidatos
     * a escolha precisa ser explicita. Que o padrao seja o balde geral tambem e a escolha segura:
     * uma rota nova esquecida sem {@code key-resolver} cai no limite comum em vez de ficar sem
     * limite algum.
     */
    @Bean
    @Primary
    public KeyResolver chaveDeRateLimit() {
        return troca -> {
            // Vem do AutenticacaoNaBordaFilter, que roda antes e ja apagou o que o cliente
            // tivesse enviado. Presenca deste cabecalho significa token validado.
            String usuario = troca.getRequest().getHeaders().getFirst(CabecalhosDeIdentidade.USER_ID);

            return Mono.just(usuario != null ? "usuario:" + usuario : "ip:" + enderecoDe(troca));
        };
    }

    /**
     * Balde estrito do login e do cadastro, sempre por IP.
     *
     * <p>Por IP e nao por usuario porque quem tenta adivinhar senha ainda nao tem token, e porque
     * chavear pelo e-mail informado deixaria um atacante trocar de alvo a cada tentativa e nunca
     * encostar no limite. O endereco de origem e o unico identificador que ele nao escolhe.
     */
    @Bean
    public KeyResolver chaveDeLogin() {
        return troca -> Mono.just("login:" + enderecoDe(troca));
    }

    /**
     * Endereco da conexao, sem consultar {@code X-Forwarded-For}: qualquer cliente pode forjar
     * esse cabecalho e ganhar um balde novo por requisicao. Atras de um proxy de verdade, o certo
     * e configurar {@code spring.cloud.gateway.server.webflux.trusted-proxies} e ler dali.
     *
     * <p>Sem endereco conhecido, todos caem na mesma chave. Errar para o lado restritivo e
     * preferivel a errar para o lado que desliga o limite.
     */
    private String enderecoDe(ServerWebExchange troca) {
        InetSocketAddress origem = troca.getRequest().getRemoteAddress();
        return origem == null ? "desconhecido" : origem.getAddress().getHostAddress();
    }
}
