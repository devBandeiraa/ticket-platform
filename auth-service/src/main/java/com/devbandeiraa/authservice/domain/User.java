package com.devbandeiraa.authservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Usuario da plataforma.
 *
 * <p>A entidade nunca conhece a senha em claro: recebe o hash ja pronto, calculado por quem
 * tem o {@code PasswordEncoder}. Isso mantem a responsabilidade de criptografia fora do dominio
 * e elimina o risco de uma senha crua ser persistida por engano.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Exigido pelo JPA. Nao usar diretamente. */
    protected User() {
    }

    private User(String email, String passwordHash, String fullName, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
        this.enabled = true;
    }

    /**
     * Cria um usuario comum, o unico papel que o cadastro publico permite.
     *
     * <p>Um ADMIN nunca nasce por auto cadastro: seria uma escalada de privilegio trivial,
     * bastando alterar o corpo da requisicao.
     */
    public static User novoUsuarioComum(String email, String passwordHash, String fullName) {
        return new User(email, passwordHash, fullName, Role.USER);
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Identidade por id, e nao pelos demais campos: duas instancias carregadas em sessoes
     * diferentes representam o mesmo usuario. Enquanto o id for nulo (entidade ainda nao
     * persistida), so a identidade da propria referencia vale.
     */
    @Override
    public boolean equals(Object outro) {
        if (this == outro) {
            return true;
        }
        if (!(outro instanceof User usuario)) {
            return false;
        }
        return id != null && id.equals(usuario.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
