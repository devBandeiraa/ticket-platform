package com.devbandeiraa.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra as pecas de seguranca compartilhadas assim que o modulo entra no classpath.
 *
 * <p>Auto configuracao, e nao um {@code @Configuration} para cada servico importar: assim basta
 * declarar a dependencia, sem precisar lembrar de um {@code @Import} em cada aplicacao — e um
 * esquecimento desses passaria despercebido ate alguem notar que as rotas estao abertas.
 *
 * <p>O {@link JwtAuthenticationFilter} deliberadamente <em>nao</em> vira bean aqui. Alem do
 * problema de dupla execucao (o Spring Boot registra qualquer bean {@code Filter} tambem no
 * container de servlets), cada servico decide onde encaixa-lo na propria cadeia, junto das
 * regras de rota que so ele conhece.
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SharedSecurityAutoConfiguration {

    /**
     * Unico bean incondicional: ler um token nao depende de servlet nem de reativo, e por isso
     * serve tanto aos servicos quanto ao api-gateway.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtTokenReader jwtTokenReader(JwtProperties propriedades) {
        return new JwtTokenReader(propriedades);
    }

    /**
     * Isolada numa classe aninhada com {@code @ConditionalOnClass} porque o
     * {@link SecurityErrorResponder} implementa interfaces de servlet.
     *
     * <p>A condicao precisa estar na classe, e nao no metodo: para avaliar um {@code @Bean} o
     * Spring inspeciona o tipo de retorno, o que carregaria {@code SecurityErrorResponder} e
     * falharia com {@code NoClassDefFoundError} onde essas interfaces nao existem. Uma classe
     * aninhada com a condicao reprovada nunca chega a ser carregada.
     *
     * <p>Quem depende disso e o api-gateway, que roda sobre WebFlux: sem este isolamento, apenas
     * declarar este modulo no classpath dele derrubaria a aplicacao na subida.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "jakarta.servlet.http.HttpServletRequest",
            "org.springframework.security.web.AuthenticationEntryPoint"})
    static class ConfiguracaoDeServlet {

        @Bean
        @ConditionalOnMissingBean
        SecurityErrorResponder securityErrorResponder(ObjectMapper objectMapper) {
            return new SecurityErrorResponder(objectMapper);
        }

        /**
         * Registrado como bean, ao contrario do {@link JwtAuthenticationFilter}.
         *
         * <p>A diferenca e proposital. Aquele precisa de um lugar especifico na cadeia do Spring
         * Security, que so cada servico conhece; este nao tem nada a decidir — vale para toda
         * requisicao, sem excecao, e nao ha servico algum que queira correlacao em parte das rotas.
         *
         * <p>O {@code FilterRegistrationBean} existe para poder fixar a ordem. Primeiro de todos:
         * um erro de autenticacao acontece antes de qualquer controller, e sem isto seria
         * justamente a falha mais dificil de investigar a unica a sair do log sem identificacao.
         */
        @Bean
        @ConditionalOnMissingBean
        FilterRegistrationBean<CorrelacaoServletFilter> correlacaoServletFilter() {
            FilterRegistrationBean<CorrelacaoServletFilter> registro =
                    new FilterRegistrationBean<>(new CorrelacaoServletFilter());
            registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registro;
        }

        @Bean
        @ConditionalOnMissingBean
        ObservacaoSemActuator observacaoSemActuator() {
            return new ObservacaoSemActuator();
        }
    }

    /**
     * A contraparte reativa, para o api-gateway.
     *
     * <p>A condicao aqui e o <em>tipo de aplicacao</em>, e nao a presenca de uma classe como na
     * configuracao acima. O motivo e que nao serviria: os dois contextos de observacao moram no
     * mesmo {@code spring-web}, entao condicionar a classe reativa daria verdadeiro tambem numa
     * aplicacao servlet, e as duas se registrariam — uma delas sem nunca casar com contexto algum.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
    static class ConfiguracaoReativa {

        @Bean
        @ConditionalOnMissingBean
        ObservacaoSemActuatorReativa observacaoSemActuatorReativa() {
            return new ObservacaoSemActuatorReativa();
        }
    }
}
