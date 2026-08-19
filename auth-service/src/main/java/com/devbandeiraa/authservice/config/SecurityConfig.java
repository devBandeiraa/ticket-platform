package com.devbandeiraa.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracao provisoria de seguranca.
 *
 * <p>Assim que o starter do Spring Security entra no classpath, todos os endpoints passam
 * a exigir HTTP Basic com uma senha gerada a cada subida — o que faria {@code /actuator/health}
 * responder 401. Esta classe apenas libera o actuator para o health check da Fase 1.
 *
 * <p>A configuracao real, com autenticacao por JWT e regras por rota, entra na Fase 2 e
 * substitui esta.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }
}
