package com.devbandeiraa.authservice.exception;

/**
 * Disparada quando o e-mail informado no cadastro ja pertence a outro usuario.
 *
 * <p>Traduzida para {@code 409 EMAIL_ALREADY_REGISTERED} pelo tratador global.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("Ja existe um usuario cadastrado com o e-mail " + email);
    }
}
