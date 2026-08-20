-- ============================================================================
--  SOMENTE DESENVOLVIMENTO — nao aplicar em producao.
--
--  Cria o primeiro administrador da plataforma.
--
--  Existe porque o cadastro publico sempre gera USER: aceitar o papel no corpo
--  da requisicao permitiria que qualquer pessoa se cadastrasse como ADMIN. Sem
--  este seed nao haveria nenhum caminho para o primeiro admin nascer.
--
--  Credenciais:
--      e-mail: admin@ticket.dev
--      senha:  admin@ticket.dev123
--
--  Este arquivo vive em db/dev, pasta que so entra nas locations do Flyway
--  quando o profile `dev` esta ativo. Rodando sem o profile, o seed simplesmente
--  nao e visto e o banco fica sem admin algum.
--
--  E uma migration repetivel (prefixo R__), e nao versionada, por dois motivos:
--  nao ocupa um numero de versao — que ficaria vago em producao e colidiria com
--  uma migration real futura — e o ON CONFLICT a torna idempotente, podendo
--  reexecutar sem efeito colateral.
-- ============================================================================

INSERT INTO users (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin@ticket.dev',
    -- BCrypt de 'admin@ticket.dev123'. Hash fixo, e nao gerado na hora, porque
    -- SQL nao calcula BCrypt e o seed precisa ser deterministico.
    '$2a$10$q5yqmDnrbOnzYFbz.bYpZeuYQ/4zalK.dvo2/MHKyyYsNB5sgsomm',
    'Administrador de Desenvolvimento',
    'ADMIN',
    TRUE,
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;
