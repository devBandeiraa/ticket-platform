package com.devbandeiraa.bookingservice.exception;

import com.devbandeiraa.bookingservice.lock.LockIndisponivelException;
import com.devbandeiraa.shared.security.ApiError;
import com.devbandeiraa.shared.security.ApiExceptionHandlerSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erros proprios da reserva.
 *
 * <p>Validacao, parametro malformado e a rede de seguranca para o inesperado vem de
 * {@link ApiExceptionHandlerSupport}.
 *
 * <p>Vale reparar em quantos destes casos sao {@code 409} e nao {@code 500}: estoque esgotado e
 * lock indisponivel sao respostas corretas a pedidos legitimos que perderam uma disputa, e nao
 * defeitos. Trata-los como erro do servidor faria os paineis acusarem falha justamente nos
 * momentos de maior sucesso de venda.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ApiExceptionHandlerSupport {

    @ExceptionHandler(EstoqueEsgotadoException.class)
    public ResponseEntity<ApiError> tratarEstoqueEsgotado(
            EstoqueEsgotadoException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.CONFLICT, "SOLD_OUT", excecao.getMessage(), requisicao, traceId);
    }

    /**
     * Evento sob disputa intensa: nao se conseguiu a vez dentro das tentativas.
     *
     * <p>{@code 409} e nao {@code 503} porque o servico esta saudavel — o que faltou foi a vez
     * na fila. Tentar de novo em seguida tende a funcionar, e e isso que o cliente deve fazer.
     */
    @ExceptionHandler(LockIndisponivelException.class)
    public ResponseEntity<ApiError> tratarLockIndisponivel(
            LockIndisponivelException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.CONFLICT, "LOCK_TIMEOUT",
                "Muitas reservas simultaneas para este evento. Tente novamente.",
                requisicao, traceId);
    }

    @ExceptionHandler(EventoNaoDisponivelException.class)
    public ResponseEntity<ApiError> tratarEventoIndisponivel(
            EventoNaoDisponivelException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.NOT_FOUND, "EVENT_NOT_AVAILABLE",
                excecao.getMessage(), requisicao, traceId);
    }

    /**
     * Nao deu para consultar o event-service.
     *
     * <p>Unico caso aqui em que a culpa e da infraestrutura, e por isso o unico {@code 5xx}. So
     * ocorre na primeira reserva de cada evento, antes de o estoque local existir.
     */
    @ExceptionHandler(EventServiceIndisponivelException.class)
    public ResponseEntity<ApiError> tratarEventServiceIndisponivel(
            EventServiceIndisponivelException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.error("[{}] {}", traceId, excecao.getMessage(), excecao);

        return responder(HttpStatus.SERVICE_UNAVAILABLE, "EVENT_SERVICE_UNAVAILABLE",
                "Nao foi possivel confirmar os dados do evento. Tente novamente em instantes.",
                requisicao, traceId);
    }

    @ExceptionHandler(ReservaNaoEncontradaException.class)
    public ResponseEntity<ApiError> tratarReservaNaoEncontrada(
            ReservaNaoEncontradaException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND",
                excecao.getMessage(), requisicao, traceId);
    }

    /**
     * A reserva nao estava no estado que a operacao exigia.
     *
     * <p>O codigo vem da propria excecao, e nao e fixo: {@code BOOKING_EXPIRED},
     * {@code BOOKING_CANCELLED} e {@code BOOKING_ALREADY_CONFIRMED} levam o frontend a telas
     * diferentes, embora todos sejam {@code 409}.
     */
    @ExceptionHandler(TransicaoDeReservaInvalidaException.class)
    public ResponseEntity<ApiError> tratarTransicaoInvalida(
            TransicaoDeReservaInvalidaException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.CONFLICT, excecao.getCodigo(),
                excecao.getMessage(), requisicao, traceId);
    }

    @ExceptionHandler(ChaveDeIdempotenciaInvalidaException.class)
    public ResponseEntity<ApiError> tratarChaveInvalida(
            ChaveDeIdempotenciaInvalidaException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.warn("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                excecao.getMessage(), requisicao, traceId);
    }
}
