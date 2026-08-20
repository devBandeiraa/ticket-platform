package com.devbandeiraa.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    }
}
