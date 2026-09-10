-- ============================================================================
--  De contador para assentos.
--
--  Ate aqui o estoque era um par de inteiros em `event_inventory`:
--  `total_tickets` e `reserved_tickets`. A garantia contra overselling morava
--  no UPDATE condicional que testava `reserved + n <= total` no proprio WHERE.
--
--  Agora cada lugar e uma linha. A garantia muda de forma — e fica mais forte.
--
--  --------------------------------------------------------------------------
--  A invariante deixa de ser uma CHECK e passa a ser estrutural
--  --------------------------------------------------------------------------
--  Com contador, era preciso uma constraint dizendo `reserved <= total`: nada
--  na estrutura impedia o numero de passar do teto, entao a regra tinha de ser
--  escrita.
--
--  Com uma linha por assento nao ha o que escrever. Existe exatamente UMA
--  linha para "Plateia fila A numero 12", e ela so sai de FREE uma vez — o
--  UPDATE condicional `WHERE status = 'FREE'` afeta zero linhas para quem
--  chegar depois. Vender o mesmo lugar duas vezes exigiria duas linhas para o
--  mesmo lugar, e a unicidade da chave natural nao permite.
--
--  O lock do Redis continua sendo otimizacao, e nao a garantia. Ele agora
--  protege ainda menos, porque a disputa e por assento e nao mais por um
--  contador unico do evento: dois compradores de lugares diferentes nao
--  colidem nem no banco.
--
--  --------------------------------------------------------------------------
--  Por que `event_inventory` sai
--  --------------------------------------------------------------------------
--  Um contador de reservados ao lado de uma linha por assento seriam duas
--  fontes de verdade para a mesma pergunta, e duas fontes divergem. Quantos
--  lugares restam passa a ser uma contagem sobre `event_seats`, que e por
--  definicao consistente com o que foi vendido.
--
--  O que a tabela ainda guardava — capacidade e preco copiados do
--  event-service — perdeu a funcao: capacidade e a contagem de assentos, e
--  preco agora e por setor, gravado em cada linha de assento.
--
--  --------------------------------------------------------------------------
--  Por que ha DUAS tabelas
--  --------------------------------------------------------------------------
--  `event_seats` e o estado ATUAL de cada lugar: livre, reservado ou vendido.
--  `booking_seats` e o registro PERMANENTE do que uma reserva pegou.
--
--  Nao sao a mesma coisa. Cancelada a reserva, o assento volta a FREE e perde
--  o ponteiro para ela — mas o usuario ainda precisa ver, em "minhas
--  reservas", quais lugares eram os dele e quanto custaram. Sem a segunda
--  tabela, cancelar apagaria a historia junto com o estado.
--
--  `booking_seats` guarda setor, fila e numero copiados, e nao apenas a
--  referencia: sao um retrato do que foi comprado. Mesma razao pela qual o
--  preco e copiado — se a casa for reformada, a reserva de ontem continua
--  dizendo onde a pessoa sentou.
-- ============================================================================

CREATE TABLE event_seats (
    id          UUID           NOT NULL,
    event_id    UUID           NOT NULL,
    -- Chave natural do assento, na forma em que o event-service a descreve.
    -- Nao ha FK para la: o evento vive em outro banco, de outro servico.
    sector_name VARCHAR(60)    NOT NULL,
    row_label   VARCHAR(4)     NOT NULL,
    seat_number INTEGER        NOT NULL,
    -- Preco do setor no momento da hidratacao. Copiado pelo mesmo motivo de
    -- sempre: a reserva de hoje precisa continuar valendo o que valia hoje.
    price       NUMERIC(10, 2) NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    -- Quem segura o lugar AGORA. Nulo quando livre.
    booking_id  UUID,

    CONSTRAINT pk_event_seats       PRIMARY KEY (id),
    -- A unicidade que substitui a antiga CHECK (reserved <= total): um lugar,
    -- uma linha. E o que torna impossivel vender o mesmo assento duas vezes.
    CONSTRAINT uk_event_seats_lugar UNIQUE (event_id, sector_name, row_label, seat_number),
    CONSTRAINT ck_event_seats_status
        CHECK (status IN ('FREE', 'RESERVED', 'SOLD')),
    CONSTRAINT ck_event_seats_numero CHECK (seat_number > 0),
    CONSTRAINT ck_event_seats_preco  CHECK (price >= 0),
    -- Livre e ter dono sao estados contraditorios, e ocupado sem dono tambem.
    -- A aplicacao ja escreve os dois campos juntos; a constraint impede que um
    -- caminho futuro escreva so um deles.
    CONSTRAINT ck_event_seats_dono
        CHECK ((status = 'FREE') = (booking_id IS NULL))
);

-- Sustenta a selecao "melhor disponivel": os livres de um evento, do mais
-- barato para o mais caro. Parcial, porque so os livres interessam a essa
-- pergunta — e num evento esgotado o indice fica vazio em vez de grande.
CREATE INDEX idx_event_seats_livres
    ON event_seats (event_id, price, id)
    WHERE status = 'FREE';

-- Liberar os assentos de uma reserva ao cancelar ou expirar.
CREATE INDEX idx_event_seats_reserva ON event_seats (booking_id);

CREATE TABLE booking_seats (
    booking_id  UUID           NOT NULL,
    seat_id     UUID           NOT NULL,
    -- Copiados, e nao lidos por join: sao o retrato do que foi comprado.
    sector_name VARCHAR(60)    NOT NULL,
    row_label   VARCHAR(4)     NOT NULL,
    seat_number INTEGER        NOT NULL,
    price       NUMERIC(10, 2) NOT NULL,

    CONSTRAINT pk_booking_seats PRIMARY KEY (booking_id, seat_id),
    CONSTRAINT fk_booking_seats_reserva
        FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE CASCADE,
    -- Intra-servico, entao a FK e legitima. Sem CASCADE no assento: apagar um
    -- assento que ja foi vendido deve falhar, e nao apagar a venda junto.
    CONSTRAINT fk_booking_seats_assento
        FOREIGN KEY (seat_id) REFERENCES event_seats (id),
    CONSTRAINT ck_booking_seats_preco CHECK (price >= 0)
);

-- ----------------------------------------------------------------------------
--  O preco unitario da reserva
--
--  Deixa de existir. Uma reserva de Plateia a 180 e Galeria a 70 nao tem um
--  preco unitario: a media seria 125, valor que nenhum ingresso custou.
--
--  Quem guarda preco agora e cada linha de `booking_seats`, e `total_price`
--  continua sendo a soma. A tela passa a poder mostrar item a item, que e mais
--  informacao do que o campo removido dava.
-- ----------------------------------------------------------------------------

ALTER TABLE bookings DROP COLUMN unit_price;

-- ----------------------------------------------------------------------------
--  Reservas anteriores
--
--  Foram feitas contra o contador, e nao existe assento a que liga-las: o
--  desenho antigo nem sabia quais lugares eram. Elas permanecem em `bookings`
--  com o total que tinham, e simplesmente nao tem linhas em `booking_seats` —
--  a tela mostra a reserva sem a lista de lugares.
--
--  Inventar assentos para elas seria pior: afirmaria que alguem sentou em
--  algum lugar especifico, o que o sistema nunca soube.
-- ----------------------------------------------------------------------------

DROP TABLE event_inventory;
