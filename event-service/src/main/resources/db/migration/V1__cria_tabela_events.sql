-- Eventos do catalogo.
--
-- total_tickets e a capacidade do evento, e e autoritativa aqui. Quantos ingressos
-- ja foram reservados nao e assunto deste servico: essa contagem vive no
-- booking-service, junto da tabela de reservas, para que a decisao "ainda cabe mais
-- um?" seja atomica com a gravacao da reserva. Ver docs/00-mapeamento.md.
CREATE TABLE events (
    id            UUID           NOT NULL,
    name          VARCHAR(150)   NOT NULL,
    description   TEXT,
    venue         VARCHAR(200)   NOT NULL,
    event_date    TIMESTAMPTZ    NOT NULL,
    total_tickets INTEGER        NOT NULL,
    price         NUMERIC(10, 2) NOT NULL,
    status        VARCHAR(20)    NOT NULL,
    -- Id do admin que criou. Sem chave estrangeira: o usuario vive no banco do
    -- auth-service, e uma FK entre bancos distintos nao existe — e nem deveria,
    -- porque acoplaria os dois servicos no nivel do schema.
    created_by    UUID           NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_events                PRIMARY KEY (id),
    CONSTRAINT ck_events_status         CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    -- Um evento sem ingresso nenhum nao e um evento; preco negativo nao existe.
    -- A aplicacao ja valida ambos, mas a constraint impede que um INSERT manual
    -- ou uma migration futura introduza um estado impossivel.
    CONSTRAINT ck_events_total_tickets  CHECK (total_tickets > 0),
    CONSTRAINT ck_events_price          CHECK (price >= 0)
);

-- Sustenta a listagem publica, que sempre filtra por status e ordena por data.
CREATE INDEX idx_events_status_event_date ON events (status, event_date);
