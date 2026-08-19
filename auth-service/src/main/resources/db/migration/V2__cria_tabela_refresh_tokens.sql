-- Refresh tokens emitidos no login.
--
-- Guarda o hash SHA-256 do token, nunca o valor original: um vazamento do banco
-- nao entrega tokens utilizaveis. Como o token e um valor aleatorio de 256 bits
-- (e nao uma senha escolhida por humano), SHA-256 basta — nao ha ataque de
-- dicionario possivel, e o custo do BCrypt so atrasaria cada refresh.
--
-- Esta e a unica chave estrangeira real do projeto, e existe porque as duas
-- tabelas pertencem ao mesmo servico.
CREATE TABLE refresh_tokens (
    id         UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens       PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash  UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user  FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- Suporta a revogacao em massa dos tokens de um usuario no logout.
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
