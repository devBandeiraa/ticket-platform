package com.devbandeiraa.authservice.security;

import com.devbandeiraa.authservice.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Faz as falhas de seguranca responderem no mesmo formato {@link ApiError} do resto da API.
 *
 * <p>Sem isto o Spring Security devolveria uma pagina de erro padrao do container, com corpo
 * diferente de todos os outros erros — e o cliente teria que tratar dois formatos.
 *
 * <p>Resolve tambem o comportamento herdado da entrega anterior: sem um
 * {@link AuthenticationEntryPoint}, uma requisicao sem token recebia 403 em vez de 401. A
 * distincao importa: 401 diz "identifique-se", 403 diz "eu sei quem voce e, e voce nao pode".
 */
@Component
public class RespostaDeErroDeSeguranca implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RespostaDeErroDeSeguranca(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Chamado quando a rota exige autenticacao e nao ha token valido. */
    @Override
    public void commence(
            HttpServletRequest requisicao, HttpServletResponse resposta, AuthenticationException excecao)
            throws IOException {

        escrever(requisicao, resposta, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHORIZED", "Autenticacao necessaria para acessar este recurso");
    }

    /** Chamado quando ha autenticacao valida, mas sem permissao para o recurso. */
    @Override
    public void handle(
            HttpServletRequest requisicao, HttpServletResponse resposta, AccessDeniedException excecao)
            throws IOException {

        escrever(requisicao, resposta, HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN", "Voce nao tem permissao para acessar este recurso");
    }

    private void escrever(
            HttpServletRequest requisicao, HttpServletResponse resposta,
            int status, String codigo, String mensagem) throws IOException {

        String traceId = UUID.randomUUID().toString().substring(0, 8);

        resposta.setStatus(status);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                resposta.getOutputStream(),
                ApiError.de(status, codigo, mensagem, requisicao.getRequestURI(), traceId));
    }
}
