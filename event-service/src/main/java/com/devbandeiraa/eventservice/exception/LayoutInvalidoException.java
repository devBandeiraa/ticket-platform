package com.devbandeiraa.eventservice.exception;

/**
 * Disparada quando a planta enviada nao descreve uma casa possivel.
 *
 * <p>Cobre o que a Bean Validation nao alcanca por depender da lista inteira, e nao de um campo:
 * setor nenhum, ou dois setores com o mesmo nome. O banco recusaria o nome repetido de qualquer
 * forma, mas o faria como violacao de constraint — um {@code 500} falando de indice, sobre um
 * dado que o usuario acabou de digitar.
 */
public class LayoutInvalidoException extends RuntimeException {

    public LayoutInvalidoException(String motivo) {
        super(motivo);
    }
}
