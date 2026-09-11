package com.devbandeiraa.bookingservice.domain;

/**
 * Estado de um lugar.
 *
 * <p>Tres estados, e nao dois: {@code RESERVED} segura o lugar durante o prazo de pagamento e
 * volta a {@code FREE} se ele vencer, enquanto {@code SOLD} e definitivo. Colapsar os dois em
 * "ocupado" perderia justamente a informacao que o job de expiracao usa para decidir o que
 * liberar.
 */
public enum SeatStatus {

    /** Disponivel para reserva. */
    FREE,

    /** Segurado por uma reserva pendente, ate o prazo de pagamento vencer. */
    RESERVED,

    /** Pago. Nao volta a ficar livre. */
    SOLD
}
