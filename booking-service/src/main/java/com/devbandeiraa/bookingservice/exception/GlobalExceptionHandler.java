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

    /**
     * O provedor de pagamento avaliou a cobranca e a negou.
     *
     * <p>{@code 402 Payment Required} e nao {@code 409}: e a unica situacao em que este status
     * significa literalmente o que diz. O frontend precisa distinguir isso de um estoque esgotado
     * — aqui o ingresso continua reservado, e trocar o meio de pagamento resolve.
     */
    @ExceptionHandler(PagamentoRecusadoException.class)
    public ResponseEntity<ApiError> tratarPagamentoRecusado(
            PagamentoRecusadoException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.info("[{}] {}", traceId, excecao.getMessage());

        return responder(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_DECLINED",
                "O pagamento nao foi autorizado. Verifique os dados e tente novamente.",
                requisicao, traceId);
    }

    /**
     * As tentativas de cobranca se esgotaram sem resposta do provedor.
     *
     * <p>Chega aqui somente depois de o retry ter falhado quatro vezes com backoff — nao e a
     * primeira falha. A reserva continua pendente e dentro do prazo, e por isso a mensagem manda
     * tentar de novo: e verdade, e o estoque ainda esta seguro.
     */
    @ExceptionHandler(PagamentoIndisponivelException.class)
    public ResponseEntity<ApiError> tratarPagamentoIndisponivel(
            PagamentoIndisponivelException excecao, HttpServletRequest requisicao) {

        String traceId = gerarTraceId();
        log.error("[{}] {}", traceId, excecao.getMessage(), excecao);

        return responder(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_PROVIDER_UNAVAILABLE",
                "O provedor de pagamento nao respondeu. Sua reserva segue valida; tente novamente.",
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
