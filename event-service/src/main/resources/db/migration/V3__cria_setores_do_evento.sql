-- ============================================================================
--  Setores do evento: o layout da casa.
--
--  Ate aqui a capacidade era um inteiro solto em `events.total_tickets`, e o
--  preco, um valor unico. Um ingresso nao tinha lugar, e todos custavam o
--  mesmo. Agora o evento tem setores — Plateia, Balcao, Camarote —, cada um
--  com o proprio preco e as proprias dimensoes.
--
--  --------------------------------------------------------------------------
--  Por que aqui ha chave estrangeira de verdade
--  --------------------------------------------------------------------------
--  A regra do projeto e que nenhuma FK atravessa servico. Esta nao atravessa:
--  `events` e `sectors` vivem no mesmo eventdb, sob o mesmo dono. Intra-servico
--  a FK e exatamente o que se quer — o ON DELETE CASCADE garante que nao sobre
--  setor orfao, e nao ha nada de distribuido a coordenar.
--
--  --------------------------------------------------------------------------
--  Por que os assentos NAO tem tabela
--  --------------------------------------------------------------------------
--  Um setor e regular: filas x lugares por fila. Guardar as 1500 linhas que
--  isso gera seria guardar o resultado de uma multiplicacao que o codigo faz
--  em uma linha — e guardar em DOIS bancos, porque o booking-service precisa
--  de uma linha por assento de qualquer forma, ja que e la que mora o ESTADO
--  de cada lugar (livre, reservado, vendido).
--
--  A identidade do assento e, portanto, a chave natural: evento, setor, fila e
--  numero. Nao ha UUID de assento a manter em concordancia entre dois bancos.
--
--  O custo aceito: layout irregular — uma fila com 18 lugares no meio de filas
--  de 20 — nao tem como ser representado, e nem assento interditado. Ambos
--  exigiriam a tabela. Fica registrado como limitacao consciente; quando a
--  primeira casa irregular aparecer, a tabela entra e a chave natural continua
--  valendo.
--
--  --------------------------------------------------------------------------
--  O que acontece com total_tickets e price em `events`
--  --------------------------------------------------------------------------
--  As duas colunas CONTINUAM existindo, e passam a ser DERIVADAS dos setores:
--  `total_tickets` e a soma de filas x lugares, e `price` e o MENOR preco entre
--  os setores — o "a partir de" que o cartao do catalogo mostra.
--
--  Nao sao redundancia por descuido; sao o que evita um N+1. A listagem publica
--  exibe preco e capacidade de nove eventos por pagina. Lidos dos setores,
--  seriam nove consultas a mais por pagina. Mantidos aqui, a listagem nao toca
--  em `sectors`, e so a tela de detalhe carrega o layout.
--
--  Quem as mantem coerentes e o EventService, num unico ponto: toda alteracao
--  de setor recalcula as duas. O booking-service segue lendo `total_tickets` e
--  `price` sem saber que setores existem — e e o que permite esta migration
--  entrar sem quebrar aquele servico, que so passa a raciocinar por assento na
--  fase seguinte.
-- ============================================================================

CREATE TABLE sectors (
    id            UUID           NOT NULL,
    event_id      UUID           NOT NULL,
    name          VARCHAR(60)    NOT NULL,
    price         NUMERIC(10, 2) NOT NULL,
    rows_count    INTEGER        NOT NULL,
    seats_per_row INTEGER        NOT NULL,
    -- Ordem de exibicao no mapa, de frente para o fundo da casa. Sem ela a
    -- ordem sairia do banco sem garantia, e o mapa mudaria de forma entre duas
    -- aberturas da mesma tela.
    display_order INTEGER        NOT NULL,

    CONSTRAINT pk_sectors             PRIMARY KEY (id),
    CONSTRAINT fk_sectors_event       FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    -- Dois setores com o mesmo nome no mesmo evento tornariam a chave natural
    -- do assento ambigua: "Plateia fila A numero 3" precisa apontar para um
    -- lugar so.
    CONSTRAINT uk_sectors_nome        UNIQUE (event_id, name),
    CONSTRAINT ck_sectors_rows        CHECK (rows_count > 0),
    CONSTRAINT ck_sectors_seats       CHECK (seats_per_row > 0),
    CONSTRAINT ck_sectors_price       CHECK (price >= 0)
);

-- Carrega os setores de um evento ja na ordem em que serao desenhados.
CREATE INDEX idx_sectors_event_ordem ON sectors (event_id, display_order);

-- ----------------------------------------------------------------------------
--  Eventos que ja existiam
--
--  Nenhum deles tem setor, e um evento sem setor nao tem mapa nem preco de onde
--  derivar. Cada um ganha um setor unico que reproduz exatamente a capacidade e
--  o preco que ja tinha, de modo que `total_tickets` e `price` continuem
--  valendo o que valiam — a migration nao muda o que se ve na tela.
--
--  As dimensoes precisam multiplicar de volta ao total exato, ou a derivacao
--  passaria a discordar da coluna. Dai a escolha do maior divisor comodo entre
--  20, 10 e 1: 420 lugares viram 21 filas de 20; 50 viram 5 de 10; um total
--  primo cai em uma fila unica, que e feio e correto.
--
--  Na pratica isto so alcanca volumes de desenvolvimento com o seed antigo.
--  Um banco novo recebe os setores desenhados no proprio seed.
-- ----------------------------------------------------------------------------

INSERT INTO sectors (id, event_id, name, price, rows_count, seats_per_row, display_order)
SELECT
    gen_random_uuid(),
    e.id,
    'Plateia',
    e.price,
    e.total_tickets / CASE
        WHEN e.total_tickets % 20 = 0 THEN 20
        WHEN e.total_tickets % 10 = 0 THEN 10
        ELSE 1
    END,
    CASE
        WHEN e.total_tickets % 20 = 0 THEN 20
        WHEN e.total_tickets % 10 = 0 THEN 10
        ELSE 1
    END,
    0
FROM events e;
