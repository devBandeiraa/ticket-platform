package com.devbandeiraa.shared.security;

import java.util.UUID;

/**
 * Identidade extraida de um access token valido.
 *
 * <p>E o que cada servico conhece sobre quem esta chamando, e vem inteiramente do token, sem
 * consulta a banco algum. Essa e a razao de ser do JWT aqui: o event-service autoriza um admin
 * sem precisar perguntar nada ao auth-service.
 *
 * @param fullName nome do portador, do claim {@code name}. <strong>Pode ser nulo</strong> em
 *                 token emitido antes da Fase 23, quando o claim nao existia. Quem exibe precisa
 *                 tratar a ausencia — o email serve de recurso, e um refresh resolve de vez
 */
public record AuthenticatedUser(UUID id, String email, Role role, String fullName) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
