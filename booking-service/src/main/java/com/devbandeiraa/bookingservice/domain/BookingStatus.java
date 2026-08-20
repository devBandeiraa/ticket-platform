package com.devbandeiraa.bookingservice.domain;

/**
 * Situacao de uma reserva.
 *
 * <p>{@code EXPIRED} e {@code CANCELLED} sao estados distintos embora tenham o mesmo efeito
 * sobre o estoque. A diferenca e de intencao: cancelada e desistencia do usuario, expirada e
 * timeout do sistema. Colapsar os dois num unico estado economizaria uma constante e apagaria
 * a informacao que o dashboard usa para saber se o prazo de pagamento esta curto demais.
 */
public enum BookingStatus {

    /** Segura o estoque, aguardando pagamento ate {@code expires_at}. */
    PENDING,

    /** Paga. Estado final. */
    CONFIRMED,

    /** Desistencia do usuario. Devolveu o estoque. */
    CANCELLED,

    /** Prazo de pagamento vencido. Devolveu o estoque. */
    EXPIRED
}
