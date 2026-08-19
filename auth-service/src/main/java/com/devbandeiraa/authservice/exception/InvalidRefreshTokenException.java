package com.devbandeiraa.authservice.exception;

/**
 * Disparada quando o refresh token e desconhecido, ja foi usado, foi revogado ou expirou.
 *
 * <p>Como na autenticacao, a mensagem nao distingue os casos: dizer "token expirado" em vez de
 * "token invalido" confirmaria a um atacante que o valor em maos ja foi valido algum dia.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Refresh token invalido ou expirado");
    }
}
