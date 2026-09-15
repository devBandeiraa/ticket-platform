package com.devbandeiraa.bookingservice.dto.request;

import com.devbandeiraa.bookingservice.domain.PaymentMethod;

/**
 * Forma de pagamento escolhida no checkout.
 *
 * <p>O corpo inteiro e opcional, e nao por comodidade: ate a migration {@code V7} este endpoint
 * nao recebia corpo algum, e exigir um agora quebraria todo cliente ja escrito. Sem corpo, ou com
 * {@code method} nulo, vale {@link PaymentMethod#CARD} — que e o que as reservas anteriores
 * implicitamente eram.
 *
 * <p>O valor cobrado <strong>nao</strong> vem aqui. Ele ja esta gravado na reserva desde a
 * criacao; aceita-lo do cliente permitiria pagar mil reais de ingresso enviando dez.
 */
public record PayBookingRequest(PaymentMethod method) {

    /** Forma efetiva, resolvendo corpo ausente e campo nulo no mesmo lugar. */
    public static PaymentMethod formaDe(PayBookingRequest requisicao) {
        if (requisicao == null || requisicao.method() == null) {
            return PaymentMethod.CARD;
        }
        return requisicao.method();
    }
}
