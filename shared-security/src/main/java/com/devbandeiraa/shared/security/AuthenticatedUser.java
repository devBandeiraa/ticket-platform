package com.devbandeiraa.shared.security;

import java.util.UUID;

/**
 * Identidade extraida de um access token valido.
 *
 * <p>E o que cada servico conhece sobre quem esta chamando, e vem inteiramente do token, sem
 * consulta a banco algum. Essa e a razao de ser do JWT aqui: o event-service autoriza um admin
 * sem precisar perguntar nada ao auth-service.
 */
public record AuthenticatedUser(UUID id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
