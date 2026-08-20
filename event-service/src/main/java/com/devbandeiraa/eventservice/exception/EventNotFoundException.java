package com.devbandeiraa.eventservice.exception;

import java.util.UUID;

/**
 * Disparada quando o evento nao existe, ou quando existe mas nao esta visivel para quem pediu.
 *
 * <p>Os dois casos produzem a mesma resposta de proposito: distinguir "nao existe" de "existe mas
 * e um rascunho" revelaria ao publico que ha um evento sendo preparado — informacao que so a
 * administracao deveria ter.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID id) {
        super("Evento nao encontrado: " + id);
    }
}
