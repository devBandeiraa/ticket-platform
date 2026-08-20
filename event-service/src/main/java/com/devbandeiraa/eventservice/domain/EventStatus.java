package com.devbandeiraa.eventservice.domain;

/**
 * Situacao de um evento no catalogo.
 *
 * <p>Persistido como texto (e nao pelo ordinal) para que a insercao de um novo estado no meio do
 * enum nao corrompa os registros existentes.
 */
public enum EventStatus {

    /** Em preparacao. Invisivel ao publico e sem reservas possiveis. */
    DRAFT,

    /** No ar. Unico estado que aparece na listagem publica e aceita reservas. */
    PUBLISHED,

    /**
     * Cancelado.
     *
     * <p>Evento nunca e apagado de verdade: reservas ja feitas continuam apontando para ele, e
     * um registro sumindo deixaria essas reservas orfas, sem como explicar ao usuario a que
     * evento se referiam.
     */
    CANCELLED
}
