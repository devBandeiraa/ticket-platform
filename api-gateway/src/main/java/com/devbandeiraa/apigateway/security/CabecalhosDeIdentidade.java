package com.devbandeiraa.apigateway.security;

import java.util.List;

/**
 * Cabecalhos com os quais o gateway afirma quem e o chamador.
 *
 * <p>Sao <em>sempre</em> removidos da requisicao que chega e reescritos pelo gateway. Aceitar o
 * valor enviado pelo cliente permitiria a qualquer um mandar {@code X-User-Role: ADMIN} e ser
 * tratado como administrador — a falha classica de gateway que confia no proprio cabecalho.
 */
public final class CabecalhosDeIdentidade {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLE = "X-User-Role";

    static final List<String> TODOS = List.of(USER_ID, USER_EMAIL, USER_ROLE);

    private CabecalhosDeIdentidade() {
    }
}
