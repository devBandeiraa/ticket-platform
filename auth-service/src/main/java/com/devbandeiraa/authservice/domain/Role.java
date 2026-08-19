package com.devbandeiraa.authservice.domain;

/**
 * Papel do usuario na plataforma.
 *
 * <p>Persistido como texto (e nao pelo ordinal) para que a insercao de um novo papel no meio
 * do enum nao corrompa os registros existentes.
 */
public enum Role {

    /** Compra ingressos. Papel atribuido a todo cadastro publico. */
    USER,

    /** Administra eventos e enxerga o painel de reservas. */
    ADMIN
}
