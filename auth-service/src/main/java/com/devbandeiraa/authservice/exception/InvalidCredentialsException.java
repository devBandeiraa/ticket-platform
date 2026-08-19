package com.devbandeiraa.authservice.exception;

/**
 * Disparada quando e-mail ou senha nao conferem, ou quando a conta esta desabilitada.
 *
 * <p>A mensagem e deliberadamente generica e identica nos tres casos: distinguir "e-mail nao
 * existe" de "senha errada" entregaria a um atacante uma forma de descobrir quais e-mails estao
 * cadastrados na plataforma.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha invalidos");
    }
}
