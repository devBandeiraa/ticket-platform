package com.devbandeiraa.bookingservice.exception;

import java.util.UUID;

/**
 * A reserva nao esta no estado que a operacao exige.
 *
 * <p>Traduzida em {@code 409}, com o codigo variando conforme o motivo — {@code BOOKING_EXPIRED},
 * {@code BOOKING_CANCELLED}, {@code BOOKING_ALREADY_CONFIRMED}. O codigo importa mais que a
 * mensagem: e por ele que o frontend decide se oferece "reservar de novo", se apenas informa, ou
 * se leva o usuario ao comprovante.
 *
 * <p>Nasce sempre da mesma situacao: um {@code UPDATE} condicional que afetou zero linhas. Ou
 * seja, o estado descrito aqui e o que o banco encontrou no instante da tentativa, e nao uma
 * leitura anterior que poderia ter envelhecido.
 */
public class TransicaoDeReservaInvalidaException extends RuntimeException {

    private final String codigo;

    public TransicaoDeReservaInvalidaException(UUID id, String codigo, String mensagem) {
        super("reserva %s: %s".formatted(id, mensagem));
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
