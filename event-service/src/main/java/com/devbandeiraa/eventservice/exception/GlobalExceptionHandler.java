package com.devbandeiraa.eventservice.exception;

import com.devbandeiraa.shared.security.ApiError;
import com.devbandeiraa.shared.security.ApiExceptionHandlerSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros proprios do catalogo de eventos.
 *
 * <p>Validacao, parametro malformado e a rede de seguranca para o inesperado vem de
 * {@link ApiExceptionHandlerSupport}. Aqui ficam apenas os casos que so existem neste dominio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandlerSupport {

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiError> tratarNaoEncontrado(
            EventNotFoundException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND",
                excecao.getMessage(), requisicao, traceId);
    }

    @ExceptionHandler(EventNotEditableException.class)
    public ResponseEntity<ApiError> tratarNaoEditavel(
            EventNotEditableException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.CONFLICT, "EVENT_NOT_EDITABLE",
                excecao.getMessage(), requisicao, traceId);
    }
}
