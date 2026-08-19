package com.devbandeiraa.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Refresh token emitido no login.
 *
 * <p>Guarda o hash do token, nunca o valor entregue ao cliente. O token em si so existe na
 * resposta HTTP: se o banco vazar, nenhum refresh token continua utilizavel.
 *
 * <p>E deliberadamente um valor opaco, e nao um JWT. Um JWT vale ate expirar e nao ha como
 * invalida-lo sem manter uma lista negra; guardando o hash, revogar e um UPDATE.
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
    }

    /**
     * Um token so serve se nao foi revogado e ainda nao expirou. Reunir as duas condicoes aqui
     * evita que cada chamador reimplemente a regra e esqueca de uma delas.
     */
    public boolean estaValidoEm(Instant momento) {
        return !revoked && expiresAt.isAfter(momento);
    }

    public void revogar() {
        this.revoked = true;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
