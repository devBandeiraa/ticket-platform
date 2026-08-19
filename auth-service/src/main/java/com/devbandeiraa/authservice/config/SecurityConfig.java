package com.devbandeiraa.authservice.config;

import com.devbandeiraa.authservice.security.JwtAuthenticationFilter;
import com.devbandeiraa.authservice.security.JwtProperties;
import com.devbandeiraa.authservice.security.JwtService;
import com.devbandeiraa.authservice.security.RespostaDeErroDeSeguranca;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracao de seguranca do auth-service.
 *
 * <p>O servico e stateless: nao ha sessao nem cookie, e a identidade de cada requisicao vem
 * inteiramente do access token.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtService jwtService,
            RespostaDeErroDeSeguranca respostaDeErro) throws Exception {

        return http
                // CSRF protege sessoes baseadas em cookie; numa API stateless consumida por
                // token ele so atrapalharia, sem acrescentar protecao.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        // Estas quatro rotas sao a porta de entrada: exigir token nelas seria
                        // um impasse, porque e justamente onde o token e obtido ou descartado.
                        .requestMatchers("/auth/register", "/auth/login", "/auth/refresh", "/auth/logout")
                            .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostaDeErro)
                        .accessDeniedHandler(respostaDeErro))
                // Antes do filtro de usuario e senha, que e onde a cadeia padrao esperaria um
                // formulario de login — inexistente aqui.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    /**
     * BCrypt com o custo padrao (2^10). O algoritmo embute o salt no proprio hash, entao nao ha
     * coluna separada para ele, e o custo pode ser elevado no futuro sem invalidar os hashes ja
     * gravados — o {@code PasswordEncoder} le o custo de dentro do hash na hora de comparar.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
