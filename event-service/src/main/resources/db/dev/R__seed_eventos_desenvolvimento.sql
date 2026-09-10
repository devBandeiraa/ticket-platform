-- ============================================================================
--  SOMENTE DESENVOLVIMENTO — nao aplicar em producao.
--
--  Popula o catalogo com eventos plausiveis e a planta de cada casa.
--
--  Existe porque, sem ele, subir a plataforma entrega uma tela vazia. O admin
--  do seed do auth-service consegue criar eventos, mas isso exige que cada
--  pessoa que clona o repositorio cadastre tudo a mao — inclusive os setores —
--  antes de conseguir ver o catalogo, a reserva ou a demo funcionando.
--
--  Migration repetivel (R__), sob a pasta db/dev, que so entra nas locations
--  do Flyway quando o profile `dev` esta ativo. Sem o profile, o catalogo
--  nasce vazio — que e o comportamento correto em producao.
--
--  --------------------------------------------------------------------------
--  Todas as casas tem lugar marcado
--  --------------------------------------------------------------------------
--  Nao ha evento de pista aqui, e a ausencia e deliberada: o dominio modela
--  assentos, e um festival de pista com 5000 lugares numerados seria uma casa
--  que nao existe. Os locais foram escolhidos entre teatros, salas de concerto
--  e centros de convencao, onde lugar marcado e o normal.
--
--  --------------------------------------------------------------------------
--  Por que as datas sao relativas
--  --------------------------------------------------------------------------
--  `event_date` e calculado a partir de NOW(), e nunca cravado em constante.
--  Um seed com data fixa apodrece: em alguns meses o catalogo volta a parecer
--  morto, agora de um jeito pior que vazio — cheio de eventos que ja
--  aconteceram. Como a validacao da aplicacao exige data futura, um seed
--  vencido tambem impediria editar qualquer um destes eventos pela tela.
--
--  As horas somadas ao dia truncado estao em UTC, que e o fuso do container do
--  Postgres: 23h UTC cai as 20h em Brasilia, horario plausivel para um show.
--
--  --------------------------------------------------------------------------
--  total_tickets e price sao derivados, e aqui estao escritos a mao
--  --------------------------------------------------------------------------
--  Na aplicacao, quem os calcula a partir dos setores e o EventService. Em SQL
--  eles precisam ser escritos junto, e precisam CONCORDAR com os setores logo
--  abaixo: `total_tickets` e a soma de filas x lugares, e `price` e o menor
--  preco entre os setores. Um teste confere os dois — se alguem mexer num
--  setor e esquecer da coluna, ele falha.
--
--  --------------------------------------------------------------------------
--  Este seed e autoritativo sobre os proprios eventos
--  --------------------------------------------------------------------------
--  Diferente da versao anterior, que apenas ignorava conflito, aqui os onze
--  eventos de id fixo sao reescritos quando o seed roda. A razao e a troca de
--  modelo: um volume de desenvolvimento antigo tem estes mesmos eventos com a
--  capacidade do desenho por quantidade, e deixa-los como estavam faria
--  `total_tickets` discordar dos setores recem-criados.
--
--  Sendo repetivel, isto so acontece quando o proprio arquivo muda. O custo
--  aceito: quem tiver editado um evento DE DEMONSTRACAO pela tela perde a
--  edicao na proxima alteracao deste seed. Eventos criados pelo admin, com id
--  proprio, nao sao tocados.
-- ============================================================================

INSERT INTO events (
    id, name, description, venue, event_date, total_tickets, price, status, image_url, created_by
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'Aurora Coletiva — noite de abertura',
        'Tres bandas da cena independente dividem a noite, com encerramento acustico no palco menor.',
        'Teatro Bradesco, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '45 days' + INTERVAL '23 hours',
        1200, 140.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'Marulho — turne Litoral Norte',
        'O trio apresenta o disco novo na integra, na mesma casa onde tocou pela primeira vez ha oito anos.',
        'Teatro Rival, Rio de Janeiro',
        date_trunc('day', NOW()) + INTERVAL '22 days' + INTERVAL '23 hours',
        400, 140.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'Vera Lumina ao vivo',
        'Show de lancamento com orquestra convidada e projecoes assinadas pelo coletivo Refrator.',
        'Espaco Unimed, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '31 days' + INTERVAL '22 hours',
        2400, 180.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'Cassiano Rios — Turne Reencontro',
        'Depois de seis anos sem subir ao palco, o cantor percorre quinze cidades. Esta e a unica data na capital paulista.',
        'Vibra Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '60 days' + INTERVAL '22 hours',
        3000, 320.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000005',
        'Quarteto Sonora — integral de Villa-Lobos',
        'Os dezessete quartetos em quatro noites. Esta e a primeira, e a unica com introducao comentada.',
        'Sala Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '12 days' + INTERVAL '24 hours',
        800, 120.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1503095396549-807759245b35?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000006',
        'Rita Valadao — stand-up Feito em Casa',
        'Uma hora de material novo sobre mudanca de cidade, familia grande e a arte de morar sozinho aos quarenta.',
        'Teatro Gamaro, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '9 days' + INTERVAL '24 hours',
        420, 90.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000007',
        'O Jardim de Inverno — temporada',
        'Montagem em cartaz por quatro semanas, com elenco de doze atores e trilha executada ao vivo.',
        'Theatro Municipal, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '38 days' + INTERVAL '23 hours',
        1500, 70.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1507924538820-ede94a04019d?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000008',
        'Distribuida 2026 — conferencia de sistemas',
        'Dia unico sobre consistencia, filas e o que quebra quando o sistema cresce. Palestras de manha, oficinas praticas a tarde.',
        'Centro de Convencoes Frei Caneca, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '75 days' + INTERVAL '12 hours',
        600, 650.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1505373877841-8d25f7d46678?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    -- Cinquenta lugares, de proposito. E o evento que a demo de concorrencia usa: com
    -- capacidade pequena, disparar duzentas reservas simultaneas esgota o estoque na hora
    -- e a tela mostra a disputa acontecendo, em vez de duzentos sucessos sem graca.
    (
        '10000000-0000-0000-0000-000000000009',
        'Sessao unica: Orquestra de Camara da Lapa',
        'Cinquenta lugares em uma sala projetada para musica de camara, que raramente abre para o publico.',
        'Sala Cecilia Meireles, Rio de Janeiro',
        date_trunc('day', NOW()) + INTERVAL '17 days' + INTERVAL '23 hours',
        50, 95.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    -- Rascunho: existe no banco e NAO pode aparecer no catalogo publico.
    (
        '10000000-0000-0000-0000-00000000000a',
        'Virada Aurora — edicao 2027',
        'Programacao em definicao. Cadastro aberto para nao perder a data.',
        'Teatro Riachuelo, Rio de Janeiro',
        date_trunc('day', NOW()) + INTERVAL '100 days' + INTERVAL '25 hours',
        2000, 200.00, 'DRAFT',
        'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    -- Cancelado: idem. Serve tambem para a listagem administrativa ter os tres status.
    (
        '10000000-0000-0000-0000-00000000000b',
        'Noite Rubra — cancelado pela casa',
        'Sessao cancelada por interdicao do espaco. Os ingressos vendidos foram reembolsados.',
        'Teatro Porto, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '50 days' + INTERVAL '23 hours',
        800, 150.00, 'CANCELLED',
        'https://images.unsplash.com/photo-1524368535928-5b5e00ddc76b?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    )
ON CONFLICT (id) DO UPDATE SET
    name          = EXCLUDED.name,
    description   = EXCLUDED.description,
    venue         = EXCLUDED.venue,
    total_tickets = EXCLUDED.total_tickets,
    price         = EXCLUDED.price,
    status        = EXCLUDED.status,
    image_url     = EXCLUDED.image_url,
    updated_at    = NOW();
-- A data NAO entra no UPDATE: recalcula-la a cada execucao empurraria o evento para a frente
-- toda vez que este arquivo mudasse, e uma reserva feita ontem passaria a apontar para um show
-- que mudou de dia sozinho. O envelhecimento e resolvido por `docker compose down -v`.

-- ----------------------------------------------------------------------------
--  A planta de cada casa
--
--  Apagados e reinseridos por evento, e nao ignorados em conflito: o setor nao
--  tem id estavel entre execucoes, e um INSERT idempotente por nome deixaria
--  para tras setores de uma versao anterior deste seed — a casa acumularia
--  balcoes que ninguem pediu.
--
--  A ordem de insercao nao define a exibicao; `display_order` define, e vai da
--  frente para o fundo da casa.
-- ----------------------------------------------------------------------------

DELETE FROM sectors WHERE event_id IN (
    '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000004',
    '10000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000006',
    '10000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000008',
    '10000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-00000000000a',
    '10000000-0000-0000-0000-00000000000b'
);

INSERT INTO sectors (id, event_id, name, price, rows_count, seats_per_row, display_order)
VALUES
    -- Aurora Coletiva: 780 + 420 = 1200
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 'Plateia',       220.00, 30, 26, 0),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 'Balcao',        140.00, 15, 28, 1),

    -- Marulho: 400
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 'Plateia',       140.00, 20, 20, 0),

    -- Vera Lumina: 1200 + 900 + 300 = 2400
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000003', 'Plateia Baixa', 340.00, 40, 30, 0),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000003', 'Plateia Alta',  240.00, 30, 30, 1),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000003', 'Mezanino',      180.00, 15, 20, 2),

    -- Cassiano Rios: 2000 + 400 + 600 = 3000
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000004', 'Plateia',       450.00, 50, 40, 0),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000004', 'Camarote',      680.00, 10, 40, 1),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000004', 'Balcao Nobre',  320.00, 20, 30, 2),

    -- Quarteto Sonora: 800
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000005', 'Plateia',       120.00, 32, 25, 0),

    -- Rita Valadao: 420
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000006', 'Plateia',        90.00, 21, 20, 0),

    -- O Jardim de Inverno: 1000 + 150 + 350 = 1500
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000007', 'Plateia',       180.00, 40, 25, 0),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000007', 'Frisas',        240.00, 10, 15, 1),
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000007', 'Galeria',        70.00, 14, 25, 2),

    -- Distribuida 2026: 600
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000008', 'Auditorio',     650.00, 30, 20, 0),

    -- Orquestra de Camara: 50 — o evento da demo de concorrencia
    (gen_random_uuid(), '10000000-0000-0000-0000-000000000009', 'Plateia',        95.00,  5, 10, 0),

    -- Virada Aurora (rascunho): 1500 + 500 = 2000
    (gen_random_uuid(), '10000000-0000-0000-0000-00000000000a', 'Plateia',       320.00, 50, 30, 0),
    (gen_random_uuid(), '10000000-0000-0000-0000-00000000000a', 'Balcao',        200.00, 25, 20, 1),

    -- Noite Rubra (cancelado): 800
    (gen_random_uuid(), '10000000-0000-0000-0000-00000000000b', 'Plateia',       150.00, 40, 20, 0);
