package com.devbandeiraa.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracao de seguranca do auth-service.
 *
 * <p>O servico e stateless: nao ha sessao nem cookie, e a autenticacao por JWT entra na proxima
 * entrega. Por ora apenas o cadastro e o actuator sao publicos, e o restante fica bloqueado.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // CSRF protege sessoes baseadas em cookie; numa API stateless consumida por
                // token ele so atrapalharia, sem acrescentar protecao.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/auth/register").permitAll()
                        .anyRequest().authenticated())
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
