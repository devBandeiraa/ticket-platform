package com.devbandeiraa.eventservice.exception;

import java.util.UUID;

/**
 * Disparada ao tentar mudar a planta da casa de um evento que ja foi publicado.
 *
 * <p>Publicado, o evento pode ter reservas — e o booking-service ja copiou a capacidade para o
 * banco dele. Trocar os setores aqui mudaria a casa por baixo de quem ja comprou: um lugar
 * vendido poderia deixar de existir, ou a capacidade cair abaixo do que ja foi vendido.
 *
 * <p>Distinta de {@link EventNotEditableException}, que trata do evento cancelado: la nada pode
 * ser alterado, aqui apenas o layout. Nome, data, descricao e capa seguem editaveis, e a
 * distincao importa para o admin saber o que ainda pode corrigir.
 */
public class LayoutNaoAlteravelException extends RuntimeException {

    public LayoutNaoAlteravelException(UUID id) {
        super("O evento " + id + " ja foi publicado; os setores nao podem mais ser alterados");
    }
}
