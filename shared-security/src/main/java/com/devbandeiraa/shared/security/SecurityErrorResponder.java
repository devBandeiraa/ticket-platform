package com.devbandeiraa.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Faz as falhas de seguranca responderem no mesmo formato {@link ApiError} do resto da API.
 *
 * <p>Sem isto o Spring Security devolveria a pagina de erro padrao do container, com corpo
 * diferente de todos os outros erros — e o cliente teria que tratar dois formatos.
 *
 * <p>A distincao entre os dois metodos importa: 401 diz "identifique-se", 403 diz "eu sei quem
 * voce e, e voce nao pode". Sem um {@link AuthenticationEntryPoint} configurado, uma requisicao
 * sem token recebe 403, o que induz o cliente a erro.
 */
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
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

        // Mesma regra do ApiExceptionHandlerSupport: o id da requisicao quando ele existe, sorteio
        // local quando nao. Um 401 e exatamente o erro que a pessoa relata ao suporte, e ele
        // precisa carregar o mesmo identificador que os logs do gateway registraram.
        String correlacao = MDC.get(CorrelacaoDeRequisicao.CHAVE_MDC);
        String traceId = correlacao != null
                ? correlacao
                : UUID.randomUUID().toString().substring(0, 8);

        resposta.setStatus(status);
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                resposta.getOutputStream(),
                ApiError.de(status, codigo, mensagem, requisicao.getRequestURI(), traceId));
    }
}
