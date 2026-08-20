package com.devbandeiraa.bookingservice.domain;

/** Situacao de uma mensagem na outbox. */
public enum OutboxStatus {

    /** Gravada junto com a transacao de negocio, aguardando publicacao. */
    PENDING,

    /** Entregue ao broker. */
    PUBLISHED,

    /**
     * Desistiu-se de publicar apos o limite de tentativas.
     *
     * <p>Estado terminal, e proposital que seja: uma mensagem que falha indefinidamente ocuparia
     * o lote a cada varredura e atrasaria todas as outras. Marcada como FAILED, ela sai do
     * caminho e fica registrada para investigacao — perder a notificacao e ruim, mas travar a
     * fila inteira por causa dela e pior.
     */
    FAILED
}
