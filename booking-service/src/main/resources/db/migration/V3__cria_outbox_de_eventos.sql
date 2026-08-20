-- Outbox transacional.
--
-- O problema que ela resolve: confirmar a reserva no banco e publicar a mensagem no RabbitMQ sao
-- dois sistemas distintos, e nao existe transacao que abranja os dois. Publicando dentro da
-- transacao, uma falha no commit deixaria uma notificacao de pagamento que nao aconteceu.
-- Publicando depois do commit, uma falha na publicacao deixaria a reserva paga sem que ninguem
-- fosse avisado — e em silencio, porque o usuario ja recebeu o 200.
--
-- Gravando o evento nesta tabela dentro da MESMA transacao que confirma a reserva, os dois fatos
-- passam a ser um so: ou ambos existem, ou nenhum existe. A publicacao vira um segundo passo,
-- feito por um processo separado que le daqui e pode tentar quantas vezes precisar.
--
-- A garantia resultante e "pelo menos uma vez", e nao "exatamente uma vez": se a publicacao der
-- certo mas a marcacao de enviada falhar, a mensagem sai de novo na proxima passada. E por isso
-- que o consumidor precisa ser idempotente — nao ha como eliminar essa janela sem transacao
-- distribuida, que e exatamente o que se quis evitar.
CREATE TABLE outbox_messages (
    id             UUID        NOT NULL,
    -- Que tipo de coisa gerou o evento e qual delas. Guardados separadamente do payload para
    -- permitir encontrar todos os eventos de uma reserva sem varrer JSON.
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id   UUID        NOT NULL,
    -- Vira a routing key na publicacao, ex.: booking.confirmed.
    type           VARCHAR(100) NOT NULL,
    -- JSONB e nao TEXT: uma outbox travada e algo que se investiga em producao, e poder
    -- consultar o conteudo da mensagem direto no SQL muda o quanto essa investigacao custa.
    payload        JSONB       NOT NULL,
    status         VARCHAR(20) NOT NULL,
    -- Quantas publicacoes ja foram tentadas. Serve para desistir de uma mensagem envenenada em
    -- vez de reprocessa-la para sempre, e para saber, olhando a tabela, se o broker esta com
    -- problema ou se e uma mensagem especifica que nao passa.
    attempts       INTEGER     NOT NULL DEFAULT 0,
    last_error     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ,

    CONSTRAINT pk_outbox_messages PRIMARY KEY (id),
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    -- published_at e status nao podem discordar.
    CONSTRAINT ck_outbox_published_at CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

-- Sustenta a varredura do publicador, que sempre busca as pendentes mais antigas primeiro.
-- Parcial porque so PENDING interessa: o indice nao cresce junto com o historico de mensagens ja
-- publicadas, que e a parte da tabela que so aumenta.
CREATE INDEX idx_outbox_pendentes
    ON outbox_messages (created_at)
    WHERE status = 'PENDING';

-- Encontrar o que foi publicado sobre uma reserva especifica, ao investigar um caso.
CREATE INDEX idx_outbox_agregado ON outbox_messages (aggregate_type, aggregate_id);
