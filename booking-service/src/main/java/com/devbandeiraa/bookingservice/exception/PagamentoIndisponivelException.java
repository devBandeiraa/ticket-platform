package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * O provedor de pagamento nao respondeu, ou respondeu que nao conseguiu avaliar a cobranca.
 *
 * <p>Separada de {@link PagamentoRecusadoException} porque a diferenca decide o comportamento: e
 * <em>esta</em> que o Resilience4j repete. Fundi-las faria o retry insistir contra uma recusa
 * definitiva, gastando quatro tentativas e a paciencia do usuario para chegar a mesma resposta.
 */
public class PagamentoIndisponivelException extends RuntimeException {

    public PagamentoIndisponivelException(UUID reservaId, String motivo, Throwable causa) {
        super("nao foi possivel cobrar a reserva %s: %s".formatted(reservaId, motivo), causa);
    }
}
