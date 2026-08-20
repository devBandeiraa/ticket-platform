package com.devbandeiraa.eventservice.config;

import com.devbandeiraa.shared.security.JwtAuthenticationFilter;
import com.devbandeiraa.shared.security.JwtTokenReader;
import com.devbandeiraa.shared.security.Role;
import com.devbandeiraa.shared.security.SecurityErrorResponder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracao de seguranca do event-service.
 *
 * <p>O servico valida o proprio token, em vez de confiar em cabecalhos preenchidos pelo gateway.
 * E defesa em profundidade: um servico que confia em {@code X-User-Role} vira admin para
 * qualquer um que consiga alcanca-lo diretamente — por um port-forward, um pod vizinho
 * comprometido ou um erro de configuracao de rede. Validar a assinatura custa microssegundos e
 * remove essa classe inteira de falha.
 *
 * <p>O gateway continuara validando na Fase 6, e isso nao e redundancia inutil: ele barra o
 * trafego invalido na borda, com rate limiting, antes que chegue aqui.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtTokenReader jwtTokenReader,
            SecurityErrorResponder respostaDeErro) throws Exception {

        return http
                // CSRF protege sessoes baseadas em cookie; numa API stateless consumida por
                // token ele so atrapalharia, sem acrescentar protecao.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        // Catalogo publico: qualquer visitante ve os eventos publicados sem
                        // precisar de conta. Restrito a GET — POST em /events nao existe, e
                        // liberar o verbo aqui abriria a porta caso passasse a existir.
                        .requestMatchers(HttpMethod.GET, "/events", "/events/**").permitAll()
                        // Regra unica para toda a administracao. Um endpoint novo sob este
                        // prefixo ja nasce protegido, sem depender de anotacao por metodo.
                        .requestMatchers("/admin/**").hasRole(Role.ADMIN.name())
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(respostaDeErro)
                        .accessDeniedHandler(respostaDeErro))
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtTokenReader),
                        UsernamePasswordAuthenticationFilter.class)
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
