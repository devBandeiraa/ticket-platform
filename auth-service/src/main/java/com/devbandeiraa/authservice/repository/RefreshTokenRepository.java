package com.devbandeiraa.authservice.repository;

import com.devbandeiraa.authservice.domain.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoga de uma vez todos os tokens ativos de um usuario, usado no logout.
     *
     * <p>Feito em uma unica instrucao em vez de carregar as entidades e alterar uma a uma: e o
     * mesmo efeito com uma ida ao banco, independente de quantas sessoes o usuario tenha aberto.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
    int revogarTodosDoUsuario(@Param("userId") UUID userId);
}
