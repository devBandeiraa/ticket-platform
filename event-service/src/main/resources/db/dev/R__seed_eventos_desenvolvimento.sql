-- ============================================================================
--  SOMENTE DESENVOLVIMENTO — nao aplicar em producao.
--
--  Popula o catalogo com eventos plausiveis.
--
--  Existe porque, sem ele, subir a plataforma entrega uma tela vazia. O admin
--  do seed do auth-service consegue criar eventos, mas isso exige que cada
--  pessoa que clona o repositorio cadastre tudo a mao antes de conseguir ver o
--  catalogo, a reserva ou a demo de concorrencia funcionando.
--
--  Mesma forma do seed do administrador: migration repetivel (R__), sob a
--  pasta db/dev, que so entra nas locations do Flyway quando o profile `dev`
--  esta ativo. Rodando sem o profile, o catalogo nasce vazio — que e o
--  comportamento correto em producao.
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
--  A ressalva honesta: o INSERT so calcula a data uma vez, na primeira
--  execucao. Um volume de desenvolvimento mantido por meses envelhece junto.
--  O caso que importa — quem clona o repositorio hoje e sobe agora — sempre
--  encontra o catalogo vivo, e um `docker compose down -v` devolve o resto.
--
--  As horas somadas ao dia truncado estao em UTC, que e o fuso do container do
--  Postgres: 23h UTC cai as 20h em Brasilia, horario plausivel para um show.
--
--  --------------------------------------------------------------------------
--  Por que ha rascunho e cancelado no meio
--  --------------------------------------------------------------------------
--  Nem todo evento do seed esta publicado, e isso e proposital. O catalogo
--  afirma na tela que "somente eventos publicados aparecem aqui"; com todos
--  publicados, a afirmacao nao teria como ser conferida. O rascunho e o
--  cancelado so aparecem na listagem administrativa, e sao a prova visivel de
--  que o filtro funciona.
--
--  --------------------------------------------------------------------------
--  Sobre as capas
--  --------------------------------------------------------------------------
--  URLs do CDN do Unsplash, com corte e qualidade fixados no proprio endereco.
--  Nomes de artista e de evento sao ficticios; os locais sao reais, o que
--  torna a listagem plausivel sem anunciar um espetaculo que alguem poderia
--  achar que esta a venda de verdade.
--
--  Se o CDN estiver fora do ar, ou se a coluna vier nula, o frontend desenha
--  um fundo derivado do nome. A tela nao depende desta rede para ficar de pe.
-- ============================================================================

INSERT INTO events (
    id, name, description, venue, event_date, total_tickets, price, status, image_url, created_by
)
VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'Aurora Coletiva — Festival de Verao',
        'Doze horas de musica em tres palcos simultaneos, com nomes da cena independente brasileira e um encerramento ao amanhecer.',
        'Autodromo de Interlagos, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '45 days' + INTERVAL '17 hours',
        5000, 390.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'Marulho — turne Litoral Norte',
        'O trio apresenta o disco novo na integra, na mesma casa onde tocou pela primeira vez ha oito anos.',
        'Circo Voador, Rio de Janeiro',
        date_trunc('day', NOW()) + INTERVAL '22 days' + INTERVAL '23 hours',
        1200, 140.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'Vera Lumina ao vivo',
        'Show de lancamento com orquestra convidada e projecoes assinadas pelo coletivo Refrator.',
        'Espaco Unimed, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '31 days' + INTERVAL '22 hours',
        8000, 280.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000004',
        'Cassiano Rios — Turne Reencontro',
        'Depois de seis anos sem subir ao palco, o cantor percorre quinze cidades. Esta e a unica data na capital paulista.',
        'Allianz Parque, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '60 days' + INTERVAL '22 hours',
        3000, 450.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    (
        '10000000-0000-0000-0000-000000000005',
        'Subsolo: noite de techno',
        'Quatro horas de set continuo com residentes da casa e um convidado anunciado apenas na porta.',
        'Audio Club, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '12 days' + INTERVAL '26 hours',
        900, 120.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=1200&h=675&fit=crop&q=80',
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
        1500, 180.00, 'PUBLISHED',
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
    -- e a tela mostra a disputa acontecendo, em vez de duzentos sucessos sem gracas.
    (
        '10000000-0000-0000-0000-000000000009',
        'Sessao unica: Orquestra de Camara da Lapa',
        'Cinquenta lugares, sem numeracao, em uma sala que foi projetada para musica de camara e raramente abre para o publico.',
        'Sala Cecilia Meireles, Rio de Janeiro',
        date_trunc('day', NOW()) + INTERVAL '17 days' + INTERVAL '23 hours',
        50, 95.00, 'PUBLISHED',
        'https://images.unsplash.com/photo-1503095396549-807759245b35?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    -- Rascunho: existe no banco e NAO pode aparecer no catalogo publico.
    (
        '10000000-0000-0000-0000-00000000000a',
        'Virada Aurora — edicao 2027',
        'Programacao em definicao. Cadastro aberto para nao perder a data.',
        'Praca Maua, Rio de Janeiro',
        date_trunc('day', NOW()) + INTERVAL '100 days' + INTERVAL '25 hours',
        2000, 320.00, 'DRAFT',
        'https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    ),
    -- Cancelado: idem. Serve tambem para a listagem administrativa ter os tres status.
    (
        '10000000-0000-0000-0000-00000000000b',
        'Noite Rubra — cancelado pela casa',
        'Sessao cancelada por interdicao do espaco. Os ingressos vendidos foram reembolsados.',
        'Cine Joia, Sao Paulo',
        date_trunc('day', NOW()) + INTERVAL '50 days' + INTERVAL '23 hours',
        800, 150.00, 'CANCELLED',
        'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=1200&h=675&fit=crop&q=80',
        '00000000-0000-0000-0000-000000000001'
    )
-- Idempotente, como o seed do administrador: reexecutar nao duplica nem sobrescreve o que
-- alguem tenha editado pela tela enquanto testava.
ON CONFLICT (id) DO NOTHING;
