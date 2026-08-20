-- Estoque local por evento.
--
-- Replica de total_tickets do event-service, mas autoritativa para a decisao de reserva.
-- O motivo esta no docs/00-mapeamento.md: a pergunta "ainda cabe mais um ingresso?" precisa
-- ser atomica com a gravacao da reserva. Se o contador vivesse no eventdb e a reserva aqui,
-- haveria uma janela entre consultar e gravar que nenhum lock fecha sem transacao distribuida.
CREATE TABLE event_inventory (
    event_id         UUID        NOT NULL,
    total_tickets    INTEGER     NOT NULL,
    reserved_tickets INTEGER     NOT NULL DEFAULT 0,
    synced_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_event_inventory PRIMARY KEY (event_id),
    CONSTRAINT ck_event_inventory_total CHECK (total_tickets > 0),

    -- A ultima rede de seguranca contra overselling, e o ponto central deste projeto.
    -- O lock distribuido do Redis pode falhar de varias formas: o TTL expira no meio da
    -- secao critica, o Redis cai, uma particao de rede deixa dois nos se acharem donos do
    -- mesmo lock. Nenhuma dessas falhas consegue gravar um estado invalido aqui, porque
    -- quem recusa e o banco, e o banco e o unico ponto pelo qual toda reserva passa.
    CONSTRAINT ck_event_inventory_reserved
        CHECK (reserved_tickets >= 0 AND reserved_tickets <= total_tickets)
);

-- Reservas.
CREATE TABLE bookings (
    id              UUID           NOT NULL,
    -- Sem chave estrangeira para event_inventory de proposito: a reserva referencia um
    -- evento do event-service, e a linha de estoque e apenas o espelho local dele. Uma FK
    -- aqui faria a reserva depender da ordem de hidratacao do estoque.
    event_id        UUID           NOT NULL,
    -- Extraido do JWT. Sem FK: o usuario vive no banco do auth-service.
    user_id         UUID           NOT NULL,
    quantity        INTEGER        NOT NULL,

    -- Snapshot do preco no ato da reserva, nao referencia ao preco atual do evento: se o
    -- admin reajustar o valor amanha, a reserva de hoje continua valendo o que valia hoje.
    unit_price      NUMERIC(10, 2) NOT NULL,
    total_price     NUMERIC(10, 2) NOT NULL,

    status          VARCHAR(20)    NOT NULL,
    -- Prazo dado ao usuario para pagar. Preenchido na criacao e mantido depois da
    -- transicao: saber ate quando a reserva valeu e o que explica, no historico, por que
    -- ela expirou.
    expires_at      TIMESTAMPTZ    NOT NULL,
    paid_at         TIMESTAMPTZ,
    idempotency_key VARCHAR(100)   NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_bookings           PRIMARY KEY (id),
    CONSTRAINT ck_bookings_quantity  CHECK (quantity > 0),
    CONSTRAINT ck_bookings_prices    CHECK (unit_price >= 0 AND total_price >= 0),
    CONSTRAINT ck_bookings_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED')),

    -- paid_at e status nao podem discordar: reserva confirmada tem instante de pagamento,
    -- e reserva nao confirmada nao tem. Escrito como igualdade entre dois booleanos, cobre
    -- as duas direcoes de uma vez.
    CONSTRAINT ck_bookings_paid_at
        CHECK ((status = 'CONFIRMED') = (paid_at IS NOT NULL)),

    -- Idempotencia por usuario, e nao global. Com chave global, bastaria um cliente
    -- adivinhar ou reaproveitar a chave de outro para que a reserva alheia fosse devolvida
    -- no lugar da sua — idempotencia viraria vetor de negacao de servico.
    CONSTRAINT uk_bookings_idempotency UNIQUE (user_id, idempotency_key)
);

-- "Minhas reservas", sempre da mais recente para a mais antiga.
CREATE INDEX idx_bookings_user_created ON bookings (user_id, created_at DESC);

-- Listagem administrativa por evento e situacao.
CREATE INDEX idx_bookings_event_status ON bookings (event_id, status);

-- Varredura do job de expiracao. Parcial porque so reservas PENDING podem expirar: o
-- indice guarda apenas a fracao da tabela que o job realmente visita, e nao cresce junto
-- com o historico de reservas ja resolvidas.
CREATE INDEX idx_bookings_pendentes_expirando
    ON bookings (expires_at)
    WHERE status = 'PENDING';
