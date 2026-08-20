package com.devbandeiraa.shared.security;

/**
 * Papel do usuario na plataforma.
 *
 * <p>Vive no modulo compartilhado porque o vocabulario de papeis atravessa servicos: o
 * auth-service o emite dentro do token, e event-service e booking-service autorizam com base
 * nele. Deixa-lo em um servico so obrigaria os demais a redeclarar o mesmo enum, e uma
 * divergencia entre as copias viraria falha de autorizacao silenciosa.
 *
 * <p>Persistido como texto (e nao pelo ordinal) para que a insercao de um novo papel no meio do
 * enum nao corrompa os registros existentes.
 */
public enum Role {

    /** Compra ingressos. Papel atribuido a todo cadastro publico. */
    USER,

    /** Administra eventos e enxerga o painel de reservas. */
    ADMIN
}
