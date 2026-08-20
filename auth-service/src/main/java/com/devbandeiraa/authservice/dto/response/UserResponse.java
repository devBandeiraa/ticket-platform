package com.devbandeiraa.authservice.dto.response;

import com.devbandeiraa.shared.security.Role;
import com.devbandeiraa.authservice.domain.User;
import java.time.Instant;
import java.util.UUID;

/**
 * Representacao publica de um usuario.
 *
 * <p>Existe justamente para que a entidade JPA nunca seja serializada direto na resposta, o que
 * exporia o hash da senha e amarraria o contrato da API ao modelo de persistencia.
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        Role role,
        Instant createdAt) {

    public static UserResponse de(User usuario) {
        return new UserResponse(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getFullName(),
                usuario.getRole(),
                usuario.getCreatedAt());
    }
}
