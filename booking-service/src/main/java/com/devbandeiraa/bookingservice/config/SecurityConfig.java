package com.devbandeiraa.bookingservice.config;

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
 * Configuracao de seguranca do booking-service.
 *
 * <p>Nao ha nada publico aqui alem do actuator, e a diferenca em relacao ao event-service e
 * significativa: o catalogo existe para ser visto por qualquer visitante, mas reservar exige
 * saber de quem e a reserva. O id do usuario vem sempre do token, nunca do corpo ou da URL.
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
                        // A documentacao descreve o contrato, nao os dados: cada caminho listado
                        // nela continua exigindo exatamente o token que ja exigia. Deixa-la atras
                        // de autenticacao criaria um impasse — o Swagger UI precisa carregar a
                        // especificacao antes de existir qualquer campo onde colar um token.
                        // Para fechar a documentacao num ambiente publico, use OPENAPI_ENABLED=false,
                        // que remove os endpoints em vez de apenas protege-los.
                        .requestMatchers("/bookings/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html").permitAll()
                        // Disponibilidade e publica, como o catalogo: quem ainda nao tem conta
                        // precisa ver se restam ingressos antes de decidir criar uma. Restrito
                        // a GET — nao existe escrita sob este caminho, e liberar o verbo abriria
                        // a porta caso passasse a existir.
                        .requestMatchers(HttpMethod.GET, "/events/*/availability").permitAll()
                        // Regra unica para toda a administracao: um endpoint novo sob este
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
