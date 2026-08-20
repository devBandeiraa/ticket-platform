package com.devbandeiraa.eventservice.exception;

import java.util.UUID;

/**
 * Disparada ao tentar alterar ou publicar um evento cancelado.
 *
 * <p>Cancelamento e terminal: reservas ja feitas apontam para aquele evento com aqueles dados, e
 * reabri-lo mudaria retroativamente o que foi vendido.
 */
public class EventNotEditableException extends RuntimeException {

    public EventNotEditableException(UUID id) {
        super("O evento " + id + " esta cancelado e nao pode mais ser alterado");
    }
}
