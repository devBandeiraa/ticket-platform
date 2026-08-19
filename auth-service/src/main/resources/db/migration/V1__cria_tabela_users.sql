-- Usuarios da plataforma. Guarda apenas o hash BCrypt da senha, nunca a senha
-- em claro. O tamanho 60 e exatamente o que o BCrypt produz.
CREATE TABLE users (
    id            UUID         NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(60)  NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users       PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    -- A aplicacao ja valida o papel, mas a constraint impede que um INSERT
    -- manual ou uma migration futura introduza um valor invalido.
    CONSTRAINT ck_users_role  CHECK (role IN ('USER', 'ADMIN'))
);
