package com.devbandeiraa.paymentsimulator.exception;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;

/**
 * Traduz os desfechos da cobranca em status HTTP.
 *
 * <p>A distincao entre os dois codigos e a informacao mais importante que este servico produz, e
 * nao um detalhe de formatacao. E dela que o cliente tira a decisao de repetir ou desistir:
 *
 * <ul>
 *   <li><b>503</b> — nao deu para avaliar a cobranca. Repita.
 *   <li><b>402</b> — a cobranca foi avaliada e negada. Repetir devolve exatamente isto de novo.
 * </ul>
 *
 * <p>Um provedor que respondesse 500 para os dois casos obrigaria quem integra a insistir numa
 * recusa definitiva, gastando tentativas para chegar a mesma resposta. Nao seguir esta separacao
 * e um defeito comum de API de pagamento de verdade.
 *
 * <p>Nao reusa o {@code ApiError} do shared-security de proposito: este servico representa um
 * terceiro, e um terceiro nao compartilha o formato de erro da nossa plataforma. Fazer o
 * booking-service lidar com um corpo estranho e parte do que se quer exercitar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PagamentoIndisponivelException.class)
    public ResponseEntity<Map<String, Object>> tratarIndisponivel(PagamentoIndisponivelException falha) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE", falha.getMessage());
    }

    @ExceptionHandler(PagamentoRecusadoException.class)
    public ResponseEntity<Map<String, Object>> tratarRecusa(PagamentoRecusadoException recusa) {
        return responder(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED", recusa.getMessage());
    }

    /**
     * Chave de idempotencia ausente e erro de quem chama, e nao falha do provedor.
     *
     * <p>Precisa ser 400 e nao 503: um cliente que esquecesse o cabecalho e recebesse 503 entraria
     * em retry contra um pedido que nunca vai passar, transformando um erro de integracao numa
     * enxurrada de tentativas.
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> tratarCabecalhoAusente(MissingRequestHeaderException falha) {
        return responder(HttpStatus.BAD_REQUEST, "MISSING_IDEMPOTENCY_KEY",
                "o cabecalho '" + falha.getHeaderName() + "' e obrigatorio");
    }

    private ResponseEntity<Map<String, Object>> responder(
            HttpStatus status, String codigo, String mensagem) {

        return ResponseEntity.status(status).body(Map.of(
                "timestamp", Instant.now().toString(),
                "code", codigo,
                "message", mensagem));
    }
}
