package com.devbandeiraa.authservice.exception;

import com.devbandeiraa.shared.security.ApiError;
import com.devbandeiraa.shared.security.ApiExceptionHandlerSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros proprios da autenticacao.
 *
 * <p>Validacao, parametro malformado e a rede de seguranca para o inesperado vem de
 * {@link ApiExceptionHandlerSupport}. Aqui ficam apenas os casos que so existem neste dominio.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandlerSupport {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> tratarEmailDuplicado(
            EmailAlreadyRegisteredException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] cadastro rejeitado: {}", traceId, excecao.getMessage());

        return responder(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED",
                excecao.getMessage(), requisicao, traceId);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> tratarCredenciaisInvalidas(
            InvalidCredentialsException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        // O motivo real (e-mail inexistente, senha errada ou conta desabilitada) fica so no log
        // do servico. A resposta e sempre a mesma, para nao revelar quais e-mails existem.
        log.warn("[{}] login recusado em {}", traceId, requisicao.getRequestURI());

        return responder(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                excecao.getMessage(), requisicao, traceId);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiError> tratarRefreshTokenInvalido(
            InvalidRefreshTokenException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] refresh recusado em {}", traceId, requisicao.getRequestURI());

        return responder(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                excecao.getMessage(), requisicao, traceId);
    }
}
