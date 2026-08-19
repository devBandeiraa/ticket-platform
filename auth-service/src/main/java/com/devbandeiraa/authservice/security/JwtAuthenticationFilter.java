package com.devbandeiraa.authservice.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Le o access token do cabecalho Authorization e popula o contexto de seguranca.
 *
 * <p>Nao e um {@code @Component} de proposito: o Spring Boot registra automaticamente qualquer
 * bean do tipo {@code Filter} no container de servlets, e o filtro acabaria executando duas
 * vezes — uma pelo container e outra pela cadeia do Spring Security. Ele e instanciado
 * diretamente na {@code SecurityConfig}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String PREFIXO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest requisicao, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {

        String cabecalho = requisicao.getHeader(HttpHeaders.AUTHORIZATION);

        if (cabecalho != null && cabecalho.startsWith(PREFIXO_BEARER)) {
            try {
                AuthenticatedUser usuario = jwtService.extrairUsuario(cabecalho.substring(PREFIXO_BEARER.length()));
                SecurityContextHolder.getContext().setAuthentication(construirAutenticacao(usuario));

            } catch (JwtException | IllegalArgumentException excecao) {
                // Token invalido nao interrompe a cadeia aqui: a requisicao segue sem
                // autenticacao e, se a rota exigir, o entry point responde 401. Isso mantem
                // um unico lugar decidindo o formato da resposta de erro.
                SecurityContextHolder.clearContext();
                log.debug("token rejeitado em {}: {}", requisicao.getRequestURI(), excecao.getMessage());
            }
        }

        cadeia.doFilter(requisicao, resposta);
    }

    /**
     * O papel vira uma authority com o prefixo {@code ROLE_}, que e o que {@code hasRole}
     * espera encontrar.
     */
    private UsernamePasswordAuthenticationToken construirAutenticacao(AuthenticatedUser usuario) {
        var permissoes = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.role().name()));
        return new UsernamePasswordAuthenticationToken(usuario, null, permissoes);
    }
}
