package com.devbandeiraa.authservice.security;

import com.devbandeiraa.authservice.domain.Role;
import java.util.UUID;

/**
 * Identidade extraida de um access token valido.
 *
 * <p>E o que a aplicacao conhece sobre quem esta chamando: vem inteiramente do token, sem
 * consulta ao banco. Essa e a razao de ser do JWT — a cada requisicao autenticada nao ha ida ao
 * banco so para descobrir quem e o usuario.
 */
public record AuthenticatedUser(UUID id, String email, Role role) {
}
