package com.devbandeiraa.eventservice.exception;

import com.devbandeiraa.shared.security.ApiError;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Converte excecoes em respostas {@link ApiError}, mantendo os controllers livres de tratamento
 * de erro.
 *
 * <p>Os tratadores de validacao e de erro inesperado repetem os do auth-service. A repeticao e
 * consciente: com apenas dois casos, extrair uma classe base para o modulo compartilhado
 * arriscaria fixar a abstracao errada cedo demais. Vale reavaliar quando um terceiro servico
 * precisar do mesmo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> tratarNaoEncontrado(
            EventNotFoundException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.de(
                HttpStatus.NOT_FOUND.value(),
                "EVENT_NOT_FOUND",
                excecao.getMessage(),
                requisicao.getRequestURI(),
                traceId));
    }

    @ExceptionHandler(EventNotEditableException.class)
    public ResponseEntity<ApiError> tratarNaoEditavel(
            EventNotEditableException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] {}", traceId, excecao.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.de(
                HttpStatus.CONFLICT.value(),
                "EVENT_NOT_EDITABLE",
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
     * Um id malformado na URL ou um status inexistente na query string sao erro do cliente, e
     * nao falha do servidor. Sem este tratador viraria 500, sugerindo um defeito que nao existe.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tratarParametroInvalido(
            MethodArgumentTypeMismatchException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] parametro invalido '{}' em {}", traceId, excecao.getName(), requisicao.getRequestURI());

        return ResponseEntity.badRequest().body(ApiError.de(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PARAMETER",
                "Valor invalido para o parametro '" + excecao.getName() + "'",
                requisicao.getRequestURI(),
                traceId));
    }

    /**
     * Rede de seguranca: qualquer excecao nao prevista vira 500 com mensagem generica. O detalhe
     * fica no log, associado ao traceId — devolve-lo ao cliente exporia detalhes internos.
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
