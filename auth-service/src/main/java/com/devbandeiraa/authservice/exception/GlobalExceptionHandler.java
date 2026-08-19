package com.devbandeiraa.authservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Converte excecoes em respostas {@link ApiError}, mantendo os controllers livres de
 * tratamento de erro.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> tratarEmailDuplicado(
            EmailAlreadyRegisteredException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] cadastro rejeitado: {}", traceId, excecao.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.de(
                HttpStatus.CONFLICT.value(),
                "EMAIL_ALREADY_REGISTERED",
                excecao.getMessage(),
                requisicao.getRequestURI(),
                traceId));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> tratarValidacao(
            MethodArgumentNotValidException excecao, HttpServletRequest requisicao) {

        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError erro : excecao.getBindingResult().getFieldErrors()) {
            // Mantem a primeira mensagem por campo: repetir todas so polui a resposta.
            campos.putIfAbsent(erro.getField(), erro.getDefaultMessage());
        }

        String traceId = gerarTraceId();
        log.warn("[{}] validacao falhou em {}: {}", traceId, requisicao.getRequestURI(), campos);

        return ResponseEntity.badRequest()
                .body(ApiError.deValidacao(requisicao.getRequestURI(), campos, traceId));
    }

    /**
     * Rede de seguranca: qualquer excecao nao prevista vira 500 com uma mensagem generica.
     * O detalhe fica no log, associado ao traceId — devolve-lo ao cliente exporia detalhes
     * internos da aplicacao.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> tratarInesperado(Exception excecao, HttpServletRequest requisicao) {
        String traceId = gerarTraceId();
        log.error("[{}] erro nao tratado em {}", traceId, requisicao.getRequestURI(), excecao);

        return ResponseEntity.internalServerError().body(ApiError.de(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "Erro interno ao processar a requisicao",
                requisicao.getRequestURI(),
                traceId));
    }

    private String gerarTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
