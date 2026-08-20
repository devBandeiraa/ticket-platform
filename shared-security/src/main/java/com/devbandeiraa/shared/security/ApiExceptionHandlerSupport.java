package com.devbandeiraa.shared.security;

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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Base dos tratadores de erro dos servicos.
 *
 * <p>Reune os casos que nao tem nada de especifico de dominio: validacao de corpo, parametro
 * malformado na URL e a rede de seguranca para o que ninguem previu. Cada servico estende esta
 * classe, anota a sua com {@code @RestControllerAdvice} e acrescenta apenas os erros do proprio
 * negocio.
 *
 * <p>A extracao seguiu a regra de tres: as duas primeiras copias, no auth-service e no
 * event-service, ficaram duplicadas de proposito, porque com dois casos ainda nao havia
 * informacao suficiente para saber o que era mesmo comum. Com o terceiro servico o padrao ficou
 * claro — e, mais importante, a duplicacao passou a ter custo real: uma mudanca no formato de
 * erro precisaria ser lembrada em tres lugares.
 *
 * <p>Note que nao ha {@code @RestControllerAdvice} aqui. Se houvesse, esta classe viraria um
 * bean por conta propria e concorreria com os tratadores dos servicos.
 */
public abstract class ApiExceptionHandlerSupport {

    /**
     * Logger da subclasse, e nao desta classe. Uma linha de log registrada como
     * {@code ApiExceptionHandlerSupport} nao diria de qual servico veio.
     */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Corpo invalido: devolve os erros por campo, para o formulario do frontend marcar
     * exatamente o que precisa ser corrigido.
     */
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
     * Um id malformado na URL ou um enum inexistente na query string sao erro do cliente, e nao
     * falha do servidor. Sem este tratador viraria 500, sugerindo um defeito que nao existe.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tratarParametroInvalido(
            MethodArgumentTypeMismatchException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] parametro invalido '{}' em {}",
                traceId, excecao.getName(), requisicao.getRequestURI());

        return responder(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                "Valor invalido para o parametro '" + excecao.getName() + "'", requisicao, traceId);
    }

    /**
     * Rede de seguranca: qualquer excecao nao prevista vira 500 com mensagem generica. O detalhe
     * fica no log, associado ao traceId — devolve-lo ao cliente exporia detalhes internos.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> tratarInesperado(
            Exception excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.error("[{}] erro nao tratado em {}", traceId, requisicao.getRequestURI(), excecao);

        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Erro interno ao processar a requisicao", requisicao, traceId);
    }

    /**
     * Monta a resposta no formato acordado.
     *
     * <p>Recebe o traceId pronto em vez de gera-lo: quem trata o erro precisa registrar a linha
     * de log com o mesmo identificador que vai para o cliente, e isso exige te-lo em maos antes
     * de montar a resposta.
     */
    protected ResponseEntity<ApiError> responder(HttpStatus status, String codigo, String mensagem,
                                                 HttpServletRequest requisicao, String traceId) {

        return ResponseEntity.status(status).body(ApiError.de(
                status.value(), codigo, mensagem, requisicao.getRequestURI(), traceId));
    }

    /** Curto de proposito: serve para achar a linha no log, nao para ser globalmente unico. */
    protected String gerarTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
