package com.devbandeiraa.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

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

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenReader jwtTokenReader(JwtProperties propriedades) {
        return new JwtTokenReader(propriedades);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityErrorResponder securityErrorResponder(ObjectMapper objectMapper) {
        return new SecurityErrorResponder(objectMapper);
    }
}
